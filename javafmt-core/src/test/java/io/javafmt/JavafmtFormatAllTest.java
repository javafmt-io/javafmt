package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.javafmt.parser.JavaParser;
import io.javafmt.parser.Staged;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The batch pipeline exists purely to share javac task setup. Its contract is that it changes
 * nothing a caller can observe, so every test here compares it against the single-file path
 * rather than asserting formatted text directly.
 */
class JavafmtFormatAllTest {

    private static final JavafmtConfig ANALYZE = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(""));

    private static List<String> dirtyCorpus(final int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> """
                               package demo;
               
                               import java.util.ArrayList;
                               import java.util.List;
               
                               final class Dirty%d {
               
                                   private final List<String> names;
               
                                   Dirty%d(List<String> names) {
                                       this.names = names;
                                   }
               
                                   List<String> upper() {
                                       ArrayList<String> out = new ArrayList<String>();
                                       for (String n : names)
                                           if (n != null)
                                               out.add(n.toUpperCase());
                                       return out;
                                   }
                               }
                               """.formatted(i, i))
                .toList();
    }

    @Test
    void matchesTheSingleFilePathOnEveryFile() {
        final var sources = dirtyCorpus(12);
        final var individually = sources.stream().map(Javafmt::format).toList();
        assertThat(outputsOf(Javafmt.formatAll(sources, JavafmtConfig.defaults(), 4))).isEqualTo(individually);
    }

    @Test
    void spansMoreThanOneChunk() {
        // The pipeline processes a bounded number of files per javac task; crossing that boundary
        // must not change any file's result or its position in the output.
        final var sources = dirtyCorpus(150);
        final var individually = sources.stream().map(Javafmt::format).toList();
        assertThat(outputsOf(Javafmt.formatAll(sources, JavafmtConfig.defaults(), 4))).isEqualTo(individually);
    }

    @ParameterizedTest
    @CsvSource({"1, 4, 16", "10, 4, 16", "100, 4, 16", "100, 1, 64", "512, 4, 32", "1000, 4, 63", "4096, 4, 64", "640, 8, 40", "640, 18, 40", "128, 0, 64"})
    void chunksAreSizedSoEveryChunkWorkerGetsAboutFour(final int files, final int threads, final int expected) {
        assertThat(BatchFormatter.chunkSize(files, threads)).isEqualTo(expected);
    }

    @Test
    void chunksSplitTheSourcesInOrder() {
        final var sources = IntStream.range(0, 32).mapToObj(i -> "class A" + i + " {}\n").toList();
        final var chunks = BatchFormatter.chunks(sources, 2);
        assertThat(chunks).hasSize(2).allSatisfy(chunk -> assertThat(chunk).hasSize(16));
        assertThat(chunks.stream().flatMap(List::stream).toList()).isEqualTo(sources);
    }

    @Test
    void unparseableFileDoesNotAffectItsNeighbours() {
        final var sources = List.of("class Good {}\n", "class Broken { this is not java", "class AlsoGood {}\n");
        final var results = Javafmt.formatAll(sources, JavafmtConfig.defaults(), 2);
        assertThat(results.getFirst().hasErrors()).isFalse();
        assertThat(results.get(1).hasErrors()).isTrue();
        assertThat(results.get(2).hasErrors()).isFalse();
        // A file that cannot be parsed round-trips unchanged, exactly as the single-file path does.
        assertThat(results.get(1).output()).isEqualTo(sources.get(1));
        assertThat(outputsOf(results)).isEqualTo(sources.stream().map(Javafmt::format).toList());
    }

    @Test
    void blankSourcesRoundTripWithoutOccupyingAParseSlot() {
        // Callers pass "" for files they already resolved (cache hits, read errors) to keep the
        // result list index-aligned with their input.
        final var sources = List.of("", "class A {}\n", "", "class B {}\n");
        final var results = Javafmt.formatAll(sources, JavafmtConfig.defaults(), 2);
        assertThat(outputsOf(results)).isEqualTo(sources.stream().map(Javafmt::format).toList());
    }

    @Test
    void emptyInputIsEmptyOutput() {
        assertThat(Javafmt.formatAll(List.of(), JavafmtConfig.defaults(), 4)).isEmpty();
    }

    @Test
    void analyzeBatchMatchesTheSingleFileAnalyzePath() {
        final var sources = analyzeSensitiveSources();
        final var individually = sources.stream()
                .map(s -> Javafmt.formatWithResult(s, ANALYZE).output())
                .toList();
        assertThat(outputsOf(Javafmt.formatAll(sources, ANALYZE, 2))).isEqualTo(individually);
    }

    @Test
    void aFileJavacCannotAttributeIsFormattedParseOnlyWithAWarning() {
        final var source = "class A {\n\n    void m() {\n        int x = 1;\n        System.out.println(x);\n    }\n}\n";
        final var results = BatchFormatter.formatChunk(
                List.of(source),
                ANALYZE,
                BatchFormatter.Renderer.INLINE,
                chunk -> BatchFormatter.fromAttributed(chunk, List.of(new Staged.Failed<>(new IllegalStateException("javac crashed")))));
        assertThat(results.getFirst().output()).isEqualTo(Javafmt.format(source));
        assertThat(results.getFirst().diagnostics())
                .singleElement()
                .isInstanceOf(Diagnostic.Warning.class)
                .extracting(Diagnostic::message, InstanceOfAssertFactories.STRING)
                .contains("attribution failed", "javac crashed");
    }

    @Test
    void aFileThatCrashesJavacAttributionDoesNotCostItsNeighbourItsAttribution() {
        final var neighbour = analyzeSensitiveSources().getLast();
        final var pathological = "class Deep {\n\n    int value = " + "1 + ".repeat(100_000) + "1;\n}\n";
        final var results = Javafmt.formatAll(List.of(neighbour, pathological), ANALYZE, 1);
        assertThat(results.getFirst()).isEqualTo(Javafmt.formatWithResult(neighbour, ANALYZE));
        assertThat(results.get(1).diagnostics()).isNotEmpty();
    }

    @Test
    void aParseErrorUnderAnalyzeStaysAParseError() {
        final var source = "class Broken { this is not java";
        final var analyzed = Javafmt.formatWithResult(source, ANALYZE);
        assertThat(analyzed.diagnostics())
                .isNotEmpty()
                .allSatisfy(d -> assertThat(d).isInstanceOf(Diagnostic.ParseError.class));
        assertThat(analyzed).isEqualTo(Javafmt.formatWithResult(source));
    }

    @Test
    void aFailedAnalyzeChunkIsRetriedFileByFileWithAttribution() {
        final var sources = analyzeSensitiveSources();
        final var analyzed = sources.stream()
                .map(s -> Javafmt.formatWithResult(s, ANALYZE).output())
                .toList();
        assertThat(analyzed).isNotEqualTo(sources.stream().map(Javafmt::format).toList());
        final var retried = JavaParser.attributing(
                "",
                session -> formatChunkFailingWhenBatched(sources, chunk -> BatchFormatter.attributedContexts(chunk, session)));
        assertThat(outputsOf(retried)).isEqualTo(analyzed);
    }

    @Test
    void aOneFileChunkThatFailsComesBackUnchangedWithAnInternalError() {
        final var sources = List.of("class A {}\n", "class B {}\n");
        final var results = formatChunkFailingWhenBatched(
                sources,
                chunk -> {
                    throw new IllegalStateException("every chunk fails");
                });
        assertThat(outputsOf(results)).isEqualTo(sources);
        assertThat(results).allSatisfy(r -> assertThat(r.diagnostics()).singleElement().isInstanceOf(Diagnostic.InternalError.class));
    }

    @Test
    void analyzeWithLintOffMatchesTheSingleFileAnalyzePath() {
        final var config = ANALYZE.withLint(false);
        final var sources = analyzeSensitiveSources();
        final var individually = sources.stream()
                .map(s -> Javafmt.formatWithResult(s, config).output())
                .toList();
        assertThat(outputsOf(Javafmt.formatAll(sources, config, 2))).isEqualTo(individually);
    }

    @Test
    void aPooledRenderTurnsAnUncaughtErrorIntoThatFilesDiagnostic() {
        try (final var pool = Executors.newSingleThreadExecutor()) {
            final var results = BatchFormatter.Renderer.on(pool)
                    .renderAll(
                            List.of("class A {}\n", "class B {}\n"),
                            List.of(
                                    () -> {
                                        throw new LinkageError("broken class");
                                    },
                                    () -> new FormatResult("class B {}\n", List.of())));
            assertThat(results.getFirst().diagnostics())
                    .singleElement()
                    .isInstanceOf(Diagnostic.InternalError.class);
            assertThat(results.get(1).output()).isEqualTo("class B {}\n");
        }
    }

    @Test
    void anInlineRenderLetsAnUncaughtErrorReachTheCaller() {
        assertThatThrownBy(
                () -> BatchFormatter.Renderer.INLINE.renderAll(
                        List.of("class A {}\n"),
                        List.of(() -> {
                            throw new LinkageError("broken class");
                        })))
                .isInstanceOf(LinkageError.class);
    }

    private static List<FormatResult> formatChunkFailingWhenBatched(
            final List<String> sources,
            final Function<List<String>, List<BatchFormatter.Prepared>> parser) {
        return BatchFormatter.formatChunk(
                sources,
                JavafmtConfig.defaults(),
                BatchFormatter.Renderer.INLINE,
                chunk -> {
                    if (chunk.size() > 1) {
                        throw new IllegalStateException("the whole chunk failed");
                    }
                    return parser.apply(chunk);
                });
    }

    private static List<String> analyzeSensitiveSources() {
        return List.of("""
                                    import java.util.List;
                                    import java.util.stream.Stream;
                    
                                    class A {
                    
                                        void test() {
                                            List<String> xs = Stream.of("a").toList();
                                            System.out.println(xs);
                                        }
                                    }
                                    """, """
                                         import java.util.*;
                         
                                         class B {
                         
                                             List<String> items = List.of();
                                         }
                                         """);
    }

    @ParameterizedTest
    @ValueSource(ints = {3000, 6000, 12000})
    void aFileDeepEnoughToExhaustTheStackDoesNotAbortTheBatch(final int terms) {
        // Generated code reaches this shape. Where the StackOverflowError lands depends on how much
        // stack the run started with: the batch-wide lint scan, a render thread, or nowhere at all
        // at the shallower depths. All this test pins is the contract that holds in every case —
        // every file still gets a result, the neighbours are formatted, and the deep file either
        // formats or round-trips unchanged with a diagnostic. What it may never do is end the run.
        final var deep = "class Deep {\n\n    int value = " + Stream.generate(() -> "1").limit(terms).collect(Collectors.joining(" + ")) + ";\n}\n";
        final var sources = List.of("class Good {}\n", deep, "class AlsoGood {}\n");
        final var results = Javafmt.formatAll(sources, JavafmtConfig.defaults(), 2);
        assertThat(results).hasSize(3);
        assertThat(results.getFirst().output()).isEqualTo("class Good {}\n");
        assertThat(results.get(2).output()).isEqualTo("class AlsoGood {}\n");
        assertThat(results.get(1).diagnostics()).allSatisfy(d -> assertThat(d).isInstanceOf(Diagnostic.InternalError.class));
        if (results.get(1).hasErrors()) {
            assertThat(results.get(1).output()).isEqualTo(deep);
        }
    }

    @Test
    void aFileTooDeepToParseOnASmallStackComesBackUnchangedWithAnInternalError() throws InterruptedException {
        final var deep = "class Deep {\n\n    int value = " + Stream.generate(() -> "1").limit(100_000).collect(Collectors.joining(" + ")) + ";\n}\n";
        final var sources = List.of("class Good {}\n", deep);
        final var results = new AtomicReference<List<FormatResult>>();
        final var failure = new AtomicReference<Throwable>();
        final var thread = new Thread(
                null,
                () -> {
                    try {
                        results.set(Javafmt.formatAll(sources, JavafmtConfig.defaults(), 1));
                    } catch (final Throwable t) {
                        failure.set(t);
                    }
                },
                "small-stack",
                512 * 1024);
        thread.start();
        thread.join();
        assertThat(failure.get()).isNull();
        assertThat(results.get().getFirst().output()).isEqualTo("class Good {}\n");
        assertThat(results.get().get(1).output()).isEqualTo(deep);
        assertThat(results.get().get(1).diagnostics())
                .singleElement()
                .isInstanceOf(Diagnostic.InternalError.class);
    }

    private static List<String> outputsOf(final List<FormatResult> results) {
        return results.stream().map(FormatResult::output).toList();
    }

    @Test
    void configReachesTheAttributedPath() {
        final var source = "class A {\n\n    void m() {\n        int x = 1;\n        System.out.println(x);\n    }\n}\n";
        assertThat(Javafmt.formatWithResult(source, ANALYZE).output()).contains("final int x");
        assertThat(Javafmt.formatWithResult(source, ANALYZE.withLint(false)).output()).doesNotContain("final int x");
    }
}
