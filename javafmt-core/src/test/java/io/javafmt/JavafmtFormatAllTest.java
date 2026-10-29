package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The batch pipeline exists purely to share javac task setup. Its contract is that it changes
 * nothing a caller can observe, so every test here compares it against the single-file path
 * rather than asserting formatted text directly.
 */
class JavafmtFormatAllTest {

    private static final String DIRTY = """
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
            """;

    private static List<String> dirtyCorpus(final int n) {
        return IntStream.range(0, n).mapToObj(i -> DIRTY.formatted(i, i)).toList();
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
        final var sources = List.of("""
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
        final var individually = sources.stream().map(s -> Javafmt.formatWithAnalyze(s, "").output()).toList();
        assertThat(outputsOf(Javafmt.formatAllWithAnalyze(sources, "", JavafmtConfig.defaults(), 2))).isEqualTo(individually);
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

    private static List<String> outputsOf(final List<FormatResult> results) {
        return results.stream().map(FormatResult::output).toList();
    }

    @Test
    void configReachesTheAttributedOutcomePath() {
        // formatWithResult(source, AttributedOutcome) used to hard-code the defaults, so a caller
        // that batch-attributed could not turn anything off.
        final var source = "class A {\n\n    void m() {\n        int x = 1;\n        System.out.println(x);\n    }\n}\n";
        final var outcome = io.javafmt.parser.JavaParser.parseAndAttribute(List.of(source), "").getFirst();
        final var withLint = Javafmt.formatWithResult(source, outcome, JavafmtConfig.defaults());
        final var withoutLint = Javafmt.formatWithResult(source, outcome, JavafmtConfig.defaults().withLint(false));
        assertThat(withLint.output()).contains("final int x");
        assertThat(withoutLint.output()).doesNotContain("final int x");
    }
}
