package io.javafmt;

import io.javafmt.cache.FormatCache;
import io.javafmt.lint.LintContext;
import io.javafmt.lint.LintEngine;
import io.javafmt.parser.AttributedUnit;
import io.javafmt.parser.AttributionSession;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParseException;
import io.javafmt.parser.ParsedUnit;
import io.javafmt.parser.Staged;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * The format pipeline behind {@link Javafmt#formatWithResult(String, JavafmtConfig)} and
 * {@link Javafmt#formatAll}: chunks the input, parses and lints each chunk batch-wide, then
 * renders. A single file is a chunk of one, rendered on the calling thread.
 */
@UtilityClass
final class BatchFormatter {

    /**
     * Most files per javac task. Measured per-file parse cost falls from 0.53 ms at a batch of one to
     * 0.016 ms at 32 and 0.008 ms at 128, so returns are already flat here; the remaining reason
     * to cap it is bounding the syntax trees each thread holds at a time.
     */
    private final int MAX_CHUNK_SIZE = 64;

    private final int MIN_CHUNK_SIZE = 16;

    private final int CHUNKS_PER_THREAD = 4;

    private final int MAX_CHUNKS_IN_FLIGHT = 4;

    /**
     * Splits the input into chunks and parses and lints at most {@link #MAX_CHUNKS_IN_FLIGHT} of
     * them at a time, so peak memory is bounded by that many chunks' syntax trees; each chunk's files
     * render on all {@code threads} threads. An attributed run stays one chunk on the calling thread,
     * because javac's {@code Types} and {@code Trees} are not thread-safe; only its render runs on
     * the pool.
     */
    List<FormatResult> formatAll(final List<String> sources, final JavafmtConfig config, final int threads) {
        final var workers = Math.max(1, threads);
        try (final var pool = newPool(workers)) {
            return formatOn(pool, workers, sources, config);
        }
    }

    List<FileResult> formatFiles(final List<Path> paths, final JavafmtConfig config, final FormatCache cache, final int threads) {
        final var workers = Math.max(1, threads);
        try (final var pool = newPool(workers)) {
            final var reads = paths.stream()
                    .map(path -> CompletableFuture.supplyAsync(() -> read(path, cache), pool))
                    .toList()
                    .stream()
                    .map(CompletableFuture::join)
                    .toList();
            final var results = formatOn(
                    pool,
                    workers,
                    reads.stream()
                            .map(read -> read instanceof FileRead.Pending(var ignored, var source) ? source : "")
                            .toList(),
                    config);
            return IntStream.range(0, reads.size())
                    .mapToObj(
                            i -> switch (reads.get(i)) {
                                case FileRead.Pending(var path, var source) -> new FileResult.Formatted(path, source, results.get(i));
                                case FileRead.Done(var done) -> done;
                            })
                    .toList();
        }
    }

    int chunkSize(final int files, final int threads) {
        final var chunkWorkers = chunkWorkers(threads);
        return chunkWorkers == 1 ? MAX_CHUNK_SIZE : Math.clamp(Math.ceilDiv(files, chunkWorkers * CHUNKS_PER_THREAD), MIN_CHUNK_SIZE, MAX_CHUNK_SIZE);
    }

    private int chunkWorkers(final int threads) {
        return Math.clamp(threads, 1, MAX_CHUNKS_IN_FLIGHT);
    }

    List<List<String>> chunks(final List<String> sources, final int threads) {
        final var size = chunkSize(sources.size(), threads);
        return IntStream.iterate(0, start -> start < sources.size(), start -> start + size)
                .mapToObj(start -> sources.subList(start, Math.min(sources.size(), start + size)))
                .toList();
    }

    private ExecutorService newPool(final int workers) {
        return Executors.newFixedThreadPool(workers, Thread.ofPlatform().name("javafmt-format-", 0).daemon(true).factory());
    }

    private List<FormatResult> formatOn(final ExecutorService pool, final int workers, final List<String> sources, final JavafmtConfig config) {
        return skippingBlanks(
                sources,
                formattable -> switch (config.analysis()) {
                    case JavafmtConfig.Analysis.ParseOnly() -> {
                        final var chunks = chunks(formattable, workers);
                        try (final var chunkPool = newPool(chunkWorkers(workers))) {
                            yield fanOut(
                                    chunkPool,
                                    chunks.stream()
                                            .<Supplier<List<FormatResult>>>map(chunk -> () -> formatParseOnly(chunk, config, Renderer.on(pool)))
                                            .toList(),
                                    (i, failure) -> chunks.get(i)
                                            .stream()
                                            .map(source -> FormatStages.internalErrorResult(source, failure))
                                            .toList())
                                    .stream()
                                    .flatMap(List::stream)
                                    .toList();
                        }
                    }
                    case JavafmtConfig.Analysis.Attributed(var classpath) -> formatAttributed(formattable, config, classpath, Renderer.on(pool));
                });
    }

    private FileRead read(final Path path, final FormatCache cache) {
        try {
            final var source = Files.readString(path, StandardCharsets.UTF_8);
            return cache.isUpToDate(path, source) ? new FileRead.Done(new FileResult.UpToDate(path)) : new FileRead.Pending(path, source);
        } catch (IOException e) {
            return new FileRead.Done(new FileResult.Unreadable(path, e));
        }
    }

    FormatResult formatOne(final String source, final JavafmtConfig config) {
        return skippingBlanks(
                List.of(source),
                sources -> switch (config.analysis()) {
                    case JavafmtConfig.Analysis.ParseOnly() -> formatParseOnly(sources, config, Renderer.INLINE);
                    case JavafmtConfig.Analysis.Attributed(var classpath) -> formatAttributed(sources, config, classpath, Renderer.INLINE);
                })
                .getFirst();
    }

    FormatResult formatParsed(final String source, final ParsedUnit unit, final JavafmtConfig config) {
        return formatChunk(
                List.of(source),
                config,
                Renderer.INLINE,
                chunk -> List.of(new Prepared(new Staged.Ok<>(LintContext.parseOnly(unit)), List.of())))
                .getFirst();
    }

    private List<FormatResult> skippingBlanks(final List<String> sources, final Function<List<String>, List<FormatResult>> format) {
        final var formattable = IntStream.range(0, sources.size())
                .filter(i -> !sources.get(i).isEmpty())
                .boxed()
                .toList();
        final var results = formattable.isEmpty() ? List.<FormatResult>of() : format.apply(formattable.stream().map(sources::get).toList());
        return scatter(sources.size(), formattable, results, i -> new FormatResult(sources.get(i), List.of()));
    }

    private List<FormatResult> formatParseOnly(final List<String> chunk, final JavafmtConfig config, final Renderer renderer) {
        return formatChunk(chunk, config, renderer, sources -> parseOnly(sources, List.of()));
    }

    private List<FormatResult> formatAttributed(
            final List<String> sources,
            final JavafmtConfig config,
            final String classpath,
            final Renderer renderer) {
        return JavaParser.attributing(classpath, session -> formatChunk(sources, config, renderer, chunk -> attributedContexts(chunk, session)));
    }

    /** Parses, lints, and renders one chunk of non-blank sources. */
    List<FormatResult> formatChunk(
            final List<String> chunk,
            final JavafmtConfig config,
            final Renderer renderer,
            final Function<List<String>, List<Prepared>> parser) {
        // Everything downstream works in LF with no BOM; how each file was written is put back on
        // its own result, so a CRLF file does not come out as a whole-file diff.
        final var encodings = chunk.stream().map(SourceEncoding::of).toList();
        final var sources = IntStream.range(0, chunk.size())
                .mapToObj(k -> encodings.get(k).normalize(chunk.get(k)))
                .toList();
        final var formatted = formatNormalized(sources, config, renderer, parser);
        return IntStream.range(0, formatted.size())
                .mapToObj(k -> FormatStages.asWritten(encodings.get(k), formatted.get(k)))
                .toList();
    }

    /**
     * The parse and lint stages run across the whole chunk, so a failure in either has no one
     * file to blame and would otherwise end the run. When that happens each file is formatted
     * again as a chunk of its own with the same {@code parser}, so an {@code --analyze} retry keeps
     * its attribution: the files that are fine still come back formatted, and whatever failed
     * reappears as the diagnostic of the one file that caused it.
     */
    private List<FormatResult> formatNormalized(
            final List<String> sources,
            final JavafmtConfig config,
            final Renderer renderer,
            final Function<List<String>, List<Prepared>> parser) {
        try {
            final var parsed = parser.apply(sources);
            final var linted = lintBatch(parsed.stream().map(Prepared::context).toList(), config);
            final var rendered = render(sources, linted, config, renderer);
            return IntStream.range(0, rendered.size())
                    .mapToObj(k -> FormatStages.withDiagnostics(rendered.get(k), parsed.get(k).warnings()))
                    .toList();
        } catch (RuntimeException | AssertionError | StackOverflowError batchFailure) {
            if (sources.size() == 1) {
                return List.of(FormatStages.internalErrorResult(sources.getFirst(), batchFailure));
            }
            return sources.stream()
                    .flatMap(source -> formatNormalized(List.of(source), config, renderer, parser).stream())
                    .toList();
        }
    }

    /** Lints every successfully parsed source in one batch, leaving parse failures untouched. */
    private List<Staged<LintEngine.Outcome>> lintBatch(final List<Staged<LintContext>> parsed, final JavafmtConfig config) {
        return Staged.mapAll(parsed, FormatStages.lintEngine(config)::lintAll);
    }

    private List<FormatResult> render(
            final List<String> sources,
            final List<Staged<LintEngine.Outcome>> linted,
            final JavafmtConfig config,
            final Renderer renderer) {
        return renderer.renderAll(
                sources,
                IntStream.range(0, sources.size())
                        .<Supplier<FormatResult>>mapToObj(i -> () -> resultFor(sources.get(i), linted.get(i), config))
                        .toList());
    }

    private FormatResult resultFor(final String source, final Staged<LintEngine.Outcome> linted, final JavafmtConfig config) {
        return switch (linted) {
            case Staged.Failed<LintEngine.Outcome>(var failure) -> FormatStages.failureToResult(source, failure);
            case Staged.Ok<LintEngine.Outcome>(var outcome) -> FormatStages.render(source, outcome, config);
        };
    }

    private <T> List<T> fanOut(final Executor pool, final List<Supplier<T>> tasks, final BiFunction<Integer, Throwable, T> onFailure) {
        final var futures = tasks.stream().map(task -> CompletableFuture.supplyAsync(task, pool)).toList();
        return IntStream.range(0, futures.size())
                .mapToObj(i -> await(futures.get(i), failure -> onFailure.apply(i, failure)))
                .toList();
    }

    private <T> T await(final Future<T> future, final Function<Throwable, T> onFailure) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return onFailure.apply(e);
        } catch (ExecutionException e) {
            return onFailure.apply(Optional.ofNullable(e.getCause()).orElse(e));
        }
    }

    private List<Prepared> parseOnly(final List<String> chunk, final List<Diagnostic> warnings) {
        return JavaParser.parseUnits(chunk)
                .stream()
                .map(parsed -> new Prepared(parsed.<LintContext>map(LintContext::parseOnly), warnings))
                .toList();
    }

    /**
     * Attributes the whole chunk in one task. A file attribution fails for, or the whole chunk when
     * attribution fails outright (a bad classpath, say), is formatted parse-only instead, carrying a
     * warning so the degradation reaches the user rather than looking like a successful
     * {@code --analyze} pass.
     */
    List<Prepared> attributedContexts(final List<String> chunk, final AttributionSession session) {
        try {
            return fromAttributed(chunk, session.attribute(chunk));
        } catch (RuntimeException e) {
            return parseOnly(chunk, List.of(FormatStages.attributionWarning(e)));
        }
    }

    List<Prepared> fromAttributed(final List<String> chunk, final List<Staged<AttributedUnit>> outcomes) {
        return IntStream.range(0, chunk.size())
                .mapToObj(
                        i -> switch (outcomes.get(i)) {
                            case Staged.Ok<AttributedUnit>(var unit) -> new Prepared(new Staged.Ok<>(LintContext.attributed(unit)), List.of());
                            case Staged.Failed<AttributedUnit>(ParseException parseError) -> new Prepared(new Staged.Failed<>(parseError), List.of());
                            case Staged.Failed<AttributedUnit>(var crash) ->
                                parseOnly(List.of(chunk.get(i)), List.of(FormatStages.attributionWarning(crash))).getFirst();
                        })
                .toList();
    }

    /**
     * Scatters {@code results} (aligned with {@code indices}) back over a list of size {@code
     * total}, filling gaps from {@code gap}.
     */
    private <T> List<T> scatter(final int total, final List<Integer> indices, final List<T> results, final IntFunction<T> gap) {
        final var byIndex = IntStream.range(0, indices.size())
                .boxed()
                .collect(Collectors.toMap(indices::get, results::get));
        return IntStream.range(0, total)
                .mapToObj(i -> Optional.ofNullable(byIndex.get(i)).orElseGet(() -> gap.apply(i)))
                .toList();
    }

    record Prepared(Staged<LintContext> context, List<Diagnostic> warnings) {}

    private sealed interface FileRead {

        record Pending(Path path, String source) implements FileRead {}

        record Done(FileResult result) implements FileRead {}
    }

    @FunctionalInterface
    interface Renderer {

        Renderer INLINE = (sources, tasks) -> tasks.stream().map(Supplier::get).toList();

        List<FormatResult> renderAll(List<String> sources, List<Supplier<FormatResult>> tasks);

        static Renderer on(final Executor pool) {
            return (sources, tasks) -> fanOut(pool, tasks, (i, failure) -> FormatStages.internalErrorResult(sources.get(i), failure));
        }
    }
}
