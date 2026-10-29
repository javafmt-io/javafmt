package io.javafmt;

import io.javafmt.lint.LintContext;
import io.javafmt.lint.LintEngine;
import io.javafmt.parser.AttributedOutcome;
import io.javafmt.parser.AttributionSession;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParseOutcome;
import io.javafmt.printer.PrintStrategy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The batch pipeline behind {@link Javafmt#formatAll} and {@link Javafmt#formatAllWithAnalyze}:
 * chunks the input, parses and lints each chunk batch-wide, then renders in parallel.
 */
@UtilityClass
final class BatchFormatter {

    /**
     * Files per javac task. Measured per-file parse cost falls from 0.53 ms at a batch of one to
     * 0.016 ms at 32 and 0.008 ms at 128, so returns are already flat here; the remaining reason
     * to cap it is holding only one chunk's syntax trees at a time.
     */
    private final int CHUNK_SIZE = 64;

    List<FormatResult> formatAll(final List<String> sources, final JavafmtConfig config, final int threads) {
        return runBatches(sources, config, threads, CHUNK_SIZE, BatchFormatter::parseOnlyContexts);
    }

    List<FormatResult> formatAllWithAnalyze(final List<String> sources, final String classpath, final JavafmtConfig config, final int threads) {
        return JavaParser.attributing(
                classpath,
                session -> runBatches(sources, config, threads, Integer.MAX_VALUE, chunk -> attributedContexts(chunk, session)));
    }

    /**
     * Splits the input into chunks, and for each chunk parses, lints batch-wide, then renders in
     * parallel. Rendering is the only stage worth parallelizing: parse and lint are dominated by
     * javac task setup, which batching already amortizes, while Doc building and printing are
     * CPU-bound and fully independent per file.
     */
    private List<FormatResult> runBatches(
            final List<String> sources,
            final JavafmtConfig config,
            final int threads,
            final int chunkSize,
            final Function<List<String>, ParsedChunk> parser) {
        if (sources.isEmpty()) {
            return List.of();
        }
        try (final var pool = Executors.newFixedThreadPool(Math.max(1, threads), Thread.ofPlatform().name("javafmt-render").daemon(true).factory())) {
            final var chunk = Math.min(chunkSize, sources.size());
            return IntStream.iterate(0, start -> start < sources.size(), start -> start + chunk)
                    .mapToObj(start -> sources.subList(start, Math.min(sources.size(), start + chunk)))
                    .flatMap(slice -> formatChunk(slice, config, pool, parser).stream())
                    .toList();
        }
    }

    /**
     * Parses, lints, and renders one chunk. Blank sources never reach javac; they resolve to
     * their own (empty) result directly, without occupying a parse slot.
     *
     * <p>The parse and lint stages run across the whole chunk, so a failure in either has no one
     * file to blame and would otherwise end the run. When that happens the chunk is re-formatted a
     * file at a time down the single-file path, which guards per file and repeats the same work:
     * the files that are fine still come back formatted, and whatever failed reappears as the
     * diagnostic of the one file that caused it.
     */
    private List<FormatResult> formatChunk(
            final List<String> chunk,
            final JavafmtConfig config,
            final ExecutorService pool,
            final Function<List<String>, ParsedChunk> parser) {
        final var formattable = IntStream.range(0, chunk.size())
                .filter(i -> !chunk.get(i).isEmpty())
                .boxed()
                .toList();
        // Everything downstream works in LF with no BOM; how each file was written is put back on
        // its own result, so a CRLF file does not come out as a whole-file diff.
        final var encodings = formattable.stream().map(i -> SourceEncoding.of(chunk.get(i))).toList();
        final var sources = IntStream.range(0, formattable.size())
                .mapToObj(k -> encodings.get(k).normalize(chunk.get(formattable.get(k))))
                .toList();
        final List<FormatResult> rendered;
        try {
            final var parsed = parser.apply(sources);
            final var linted = lintBatch(parsed.contexts(), config);
            final var printed = renderInParallel(sources, linted, config, pool);
            rendered = IntStream.range(0, printed.size())
                    .mapToObj(k -> Javafmt.asWritten(encodings.get(k), Javafmt.withWarnings(printed.get(k), parsed.warnings())))
                    .toList();
        } catch (RuntimeException | AssertionError | StackOverflowError batchFailure) {
            return scatter(
                    chunk.size(),
                    formattable,
                    asWritten(encodings, formatOneAtATime(sources, config)),
                    i -> new FormatResult(chunk.get(i), List.of()));
        }
        return scatter(chunk.size(), formattable, rendered, i -> new FormatResult(chunk.get(i), List.of()));
    }

    private List<FormatResult> asWritten(final List<SourceEncoding> encodings, final List<FormatResult> results) {
        return IntStream.range(0, results.size())
                .mapToObj(k -> Javafmt.asWritten(encodings.get(k), results.get(k)))
                .toList();
    }

    private List<FormatResult> formatOneAtATime(final List<String> sources, final JavafmtConfig config) {
        return sources.stream()
                .map(source -> Javafmt.formatWithResult(source, config, PrintStrategy.wadlerLindig()))
                .toList();
    }

    /** Lints every successfully parsed source in one batch, leaving parse failures untouched. */
    private List<Linted> lintBatch(final List<Prepared> parsed, final JavafmtConfig config) {
        final var lintable = IntStream.range(0, parsed.size())
                .filter(i -> parsed.get(i) instanceof Prepared.Ready)
                .boxed()
                .toList();
        final var contexts = lintable.stream()
                .flatMap(i -> parsed.get(i) instanceof Prepared.Ready r ? Stream.of(r.context()) : Stream.<LintContext>empty())
                .toList();
        final var outcomes = config.lint()
                ? Javafmt.LINT_ENGINE.lintAll(contexts)
                : contexts.stream()
                        .<LintEngine.BatchOutcome>map(c -> new LintEngine.BatchOutcome.Ok(Javafmt.lintIfEnabled(c, config)))
                        .toList();
        final var linted = IntStream.range(0, lintable.size())
                .mapToObj(
                        k -> switch (outcomes.get(k)) {
                            case LintEngine.BatchOutcome.Ok ok -> (Linted) new Linted.Ready(ok.outcome());
                            case LintEngine.BatchOutcome.Failed failed -> new Linted.Failed(failed.error());
                        })
                .toList();
        return scatter(
                parsed.size(),
                lintable,
                linted,
                i -> switch (parsed.get(i)) {
                    case Prepared.Failed f -> new Linted.Failed(f.failure());
                    case Prepared.Ready ignored -> throw new IllegalStateException("unlinted ready context #" + i);
                });
    }

    private List<FormatResult> renderInParallel(
            final List<String> sources,
            final List<Linted> linted,
            final JavafmtConfig config,
            final ExecutorService pool) {
        final var futures = IntStream.range(0, sources.size())
                .mapToObj(i -> pool.submit(() -> resultFor(sources.get(i), linted.get(i), config)))
                .toList();
        return IntStream.range(0, sources.size())
                .mapToObj(i -> await(sources.get(i), futures.get(i)))
                .toList();
    }

    private FormatResult resultFor(final String source, final Linted linted, final JavafmtConfig config) {
        return switch (linted) {
            case Linted.Failed failed -> Javafmt.failureToResult(source, failed.failure());
            case Linted.Ready ready -> {
                try {
                    yield Javafmt.render(source, ready.outcome(), config, PrintStrategy.wadlerLindig());
                } catch (RuntimeException | AssertionError | StackOverflowError t) {
                    yield Javafmt.internalErrorResult(source, t);
                }
            }
        };
    }

    /**
     * Collects one render result. A render task that failed in a way {@link #resultFor} did not
     * catch is this file's problem and nobody else's, so it becomes this file's diagnostic rather
     * than an exception that loses every other result in the run.
     */
    private FormatResult await(final String source, final Future<FormatResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Javafmt.internalErrorResult(source, e);
        } catch (ExecutionException e) {
            return Javafmt.internalErrorResult(source, Optional.ofNullable(e.getCause()).orElse(e));
        }
    }

    private ParsedChunk parseOnlyContexts(final List<String> chunk) {
        return new ParsedChunk(
                JavaParser.parseUnits(chunk)
                        .stream()
                        .map(
                                outcome -> switch (outcome) {
                                    case ParseOutcome.Ok ok -> (Prepared) new Prepared.Ready(LintContext.parseOnly(ok.unit()));
                                    case ParseOutcome.Failed failed -> new Prepared.Failed(failed.error());
                                })
                        .toList(),
                List.of());
    }

    /**
     * Attributes the whole chunk in one task. If attribution fails outright (a bad classpath,
     * say), falls back to parse-only so the run still formats, matching
     * {@link Javafmt#formatWithAnalyze}'s single-file behaviour — but carries the reason out as
     * a warning, so the degradation reaches the user instead of the run merely looking like a
     * successful {@code --analyze} pass.
     */
    private ParsedChunk attributedContexts(final List<String> chunk, final AttributionSession session) {
        try {
            return new ParsedChunk(
                    session.attribute(chunk)
                            .stream()
                            .map(
                                    outcome -> switch (outcome) {
                                        case AttributedOutcome.Ok ok -> (Prepared) new Prepared.Ready(LintContext.attributed(ok.unit()));
                                        case AttributedOutcome.Failed failed -> new Prepared.Failed(failed.error());
                                    })
                            .toList(),
                    List.of());
        } catch (RuntimeException e) {
            return new ParsedChunk(parseOnlyContexts(chunk).contexts(), List.of(Javafmt.attributionWarning(e)));
        }
    }

    /**
     * Scatters {@code results} (aligned with {@code indices}) back over a list of size {@code
     * total}, filling gaps from {@code gap}. {@code gap} runs only for indices absent from {@code
     * indices} — some callers' gap functions (see {@link #lintBatch}) are unsafe to run otherwise.
     */
    private <T> List<T> scatter(final int total, final List<Integer> indices, final List<T> results, final IntFunction<T> gap) {
        final var byIndex = IntStream.range(0, indices.size())
                .boxed()
                .collect(Collectors.toMap(indices::get, results::get));
        return IntStream.range(0, total)
                .mapToObj(i -> Optional.ofNullable(byIndex.get(i)).orElseGet(() -> gap.apply(i)))
                .toList();
    }

    /** Parsed sources for one chunk, plus any warning that applies to every file in it. */
    private record ParsedChunk(List<Prepared> contexts, List<Diagnostic> warnings) {}

    /** A source ready for linting, or the parse/attribution failure that stands in for it. */
    sealed interface Prepared {

        record Ready(LintContext context) implements Prepared {}

        record Failed(RuntimeException failure) implements Prepared {}
    }

    /** A linted source, or the failure that stands in for it. */
    sealed interface Linted {

        record Ready(LintEngine.Outcome outcome) implements Linted {}

        record Failed(RuntimeException failure) implements Linted {}
    }
}
