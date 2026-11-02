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
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
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
import java.util.stream.LongStream;
import java.util.stream.Stream;

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

    private final long ATTRIBUTION_BATCH_SIZE = 4L << 20;

    private final long SLICE_SIZE = 32L << 20;

    private final long HEAP_PER_ATTRIBUTION_BATCH = 768L << 20;

    private final int MAX_ATTRIBUTED_PASSES = 10;

    private final int MAX_BATCH_FILES = 128;

    private final String JAVAC_FATAL_ERROR = "com.sun.tools.javac.util.FatalError";

    /**
     * Splits the input into chunks and parses and lints at most {@link #MAX_CHUNKS_IN_FLIGHT} of
     * them at a time, so peak memory is bounded by that many chunks' syntax trees; each chunk's files
     * render on all {@code threads} threads. An attributed run is split into batches of at most
     * {@link #MAX_BATCH_FILES} sources and {@link #ATTRIBUTION_BATCH_SIZE} characters, attributed one
     * after another on the calling thread, because javac's {@code Types} and {@code Trees} are not
     * thread-safe; only their render runs on the pool.
     */
    List<FormatResult> formatAll(final List<String> sources, final JavafmtConfig config, final int threads) {
        final var workers = Math.max(1, threads);
        try (final var pool = newPool(workers)) {
            return formatOn(pool, workers, sources, sources.stream().map(_ -> Set.<String>of()).toList(), config);
        }
    }

    List<FileResult> formatFiles(final List<Path> paths, final JavafmtConfig config, final FormatCache cache, final int threads) {
        try (final var results = formatFilesLazily(paths, config, cache, threads)) {
            return results.toList();
        }
    }

    Stream<FileResult> formatFilesLazily(final List<Path> paths, final JavafmtConfig config, final FormatCache cache, final int threads) {
        final var workers = Math.max(1, threads);
        final var attributed = config.analysis() instanceof JavafmtConfig.Analysis.Attributed;
        final var batchesAtOnce = Math.clamp(
                Runtime.getRuntime().maxMemory() / HEAP_PER_ATTRIBUTION_BATCH, 1, Math.min(workers, MAX_CHUNKS_IN_FLIGHT));
        return formatFilesLazily(paths, config, cache, threads, attributed ? batchesAtOnce : 1);
    }

    Stream<FileResult> formatFilesLazily(final List<Path> paths,
                                         final JavafmtConfig config,
                                         final FormatCache cache,
                                         final int threads,
                                         final int slicesAtOnce) {
        final var workers = Math.max(1, threads);
        final var pool = newPool(workers);
        final var slicePool = newPool(slicesAtOnce);
        final var projectFiles = new ConcurrentHashMap<Path, ProjectFile>();
        final var sizes = paths.stream().map(BatchFormatter::sizeOf).toList();
        final var slices = config.analysis() instanceof JavafmtConfig.Analysis.Attributed
                ? batches(sizes, paths.stream().map(path -> Objects.requireNonNullElse(path.toAbsolutePath().getParent(), path)).toList())
                : slices(sizes, SLICE_SIZE);
        return IntStream.iterate(0, start -> start < slices.size(), start -> start + slicesAtOnce)
                .mapToObj(start -> slices.subList(start, Math.min(slices.size(), start + slicesAtOnce)))
                .flatMap(
                        wave -> fanOut(
                                slicePool,
                                wave.stream()
                                        .<Supplier<List<FileResult>>>map(
                                                slice -> () -> formatSlice(
                                                        slice.stream().map(paths::get).toList(), config, cache, pool, workers, projectFiles))
                                        .toList(),
                                (_, failure) -> {
                                    throw new IllegalStateException("formatting a batch of files failed", failure);
                                })
                                .stream())
                .flatMap(List::stream)
                .onClose(() -> {
                    slicePool.close();
                    pool.close();
                });
    }

    private List<FileResult> formatSlice(final List<Path> paths,
                                         final JavafmtConfig config,
                                         final FormatCache cache,
                                         final ExecutorService pool,
                                         final int workers,
                                         final Map<Path, ProjectFile> projectFiles) {
        final var reads = paths.stream()
                .map(path -> CompletableFuture.supplyAsync(() -> read(path, cache, projectFiles), pool))
                .toList()
                .stream()
                .map(BatchFormatter::joined)
                .toList();
        final var results = formatOn(
                pool, workers,
                reads.stream()
                        .map(read -> read instanceof FileRead.Pending(var _, var source, var _) ? source : "")
                        .toList(),
                reads.stream()
                        .map(read -> read instanceof FileRead.Pending(var _, var _, var analyzeSkip) ? analyzeSkip : Set.<String>of())
                        .toList(),
                config);
        return IntStream.range(0, reads.size())
                .mapToObj(
                        i -> switch (reads.get(i)) {
                            case FileRead.Pending(var path, var source, var _) -> new FileResult.Formatted(path, source, results.get(i));
                            case FileRead.Done(var done) -> done;
                        })
                .toList();
    }

    private <T> T joined(final CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (final CompletionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private long sizeOf(final Path path) {
        try {
            return Files.size(path);
        } catch (final IOException _) {
            return 1;
        }
    }

    List<List<Integer>> batches(final List<Long> weights, final List<?> directories) {
        return Stream.iterate(0, start -> start < weights.size(), start -> batchEnd(directories, start))
                .flatMap(start -> withinBudget(IntStream.range(start, batchEnd(directories, start)).boxed().toList(), weights))
                .toList();
    }

    private int batchEnd(final List<?> directories, final int start) {
        final var limit = Math.min(directories.size(), start + MAX_BATCH_FILES);
        return IntStream.rangeClosed(start + 1, limit)
                .filter(end -> end == limit || (end - start >= MAX_BATCH_FILES / 2 && !directories.get(end).equals(directories.get(end - 1))))
                .findFirst()
                .orElse(limit);
    }

    private Stream<List<Integer>> withinBudget(final List<Integer> batch, final List<Long> weights) {
        if (batch.size() == 1 || batch.stream().mapToLong(weights::get).sum() <= ATTRIBUTION_BATCH_SIZE) {
            return Stream.of(batch);
        }
        final var middle = batch.size() / 2;
        return Stream.concat(withinBudget(batch.subList(0, middle), weights), withinBudget(batch.subList(middle, batch.size()), weights));
    }

    List<List<Integer>> slices(final List<Long> weights, final long budget) {
        final var ends = LongStream.concat(LongStream.of(0), weights.stream().mapToLong(weight -> Math.max(1, weight))).toArray();
        Arrays.parallelPrefix(ends, Long::sum);
        return Stream.iterate(0, start -> start < weights.size(), start -> sliceEnd(ends, start, budget))
                .map(start -> IntStream.range(start, sliceEnd(ends, start, budget)).boxed().toList())
                .toList();
    }

    private int sliceEnd(final long[] ends, final int start, final long budget) {
        final var found = Arrays.binarySearch(ends, start + 1, ends.length, ends[start] + budget);
        return Math.max(start + 1, found >= 0 ? found : -found - 2);
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

    private List<FormatResult> formatOn(final ExecutorService pool,
                                        final int workers,
                                        final List<String> sources,
                                        final List<Set<String>> analyzeSkips,
                                        final JavafmtConfig config) {
        return skippingBlanks(
                sources, analyzeSkips,
                (formattable, skips) -> switch (config.analysis()) {
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
                    case JavafmtConfig.Analysis.Attributed(var classpath) ->
                        batches(formattable.stream().map(source -> (long) source.length()).toList(), formattable.stream().map(_ -> "").toList())
                                .stream()
                                .flatMap(
                                        batch -> formatAttributed(
                                                batch.stream().map(formattable::get).toList(), batch.stream().map(skips::get).toList(), config,
                                                classpath, Renderer.on(pool))
                                                .stream())
                                .toList();
                });
    }

    private FileRead read(final Path path, final FormatCache cache, final Map<Path, ProjectFile> projectFiles) {
        try {
            final var source = Files.readString(path, StandardCharsets.UTF_8);
            final var absolute = path.toAbsolutePath();
            final var projectFile = projectFiles.computeIfAbsent(Objects.requireNonNullElse(absolute.getParent(), absolute), ProjectFile::forDirectory);
            if (projectFile instanceof ProjectFile.Invalid invalid) {
                return new FileRead.Done(new FileResult.Formatted(path, source, FormatStages.configErrorResult(source, invalid)));
            }
            return cache.isUpToDate(path, source)
                    ? new FileRead.Done(new FileResult.UpToDate(path))
                    : new FileRead.Pending(path, source, projectFile.analyzeSkip());
        } catch (final IOException e) {
            return new FileRead.Done(new FileResult.Unreadable(path, e));
        }
    }

    FormatResult formatOne(final String source, final JavafmtConfig config, final ProjectFile projectFile) {
        if (projectFile instanceof ProjectFile.Invalid invalid) {
            return FormatStages.configErrorResult(source, invalid);
        }
        return skippingBlanks(
                List.of(source), List.of(projectFile.analyzeSkip()),
                (sources, skips) -> switch (config.analysis()) {
                    case JavafmtConfig.Analysis.ParseOnly() -> formatParseOnly(sources, config, Renderer.INLINE);
                    case JavafmtConfig.Analysis.Attributed(var classpath) -> formatAttributed(sources, skips, config, classpath, Renderer.INLINE);
                })
                .getFirst();
    }

    FormatResult formatParsed(final String source, final ParsedUnit unit, final JavafmtConfig config) {
        return formatChunk(
                List.of(source), config, Renderer.INLINE, _ -> List.of(new Prepared(new Staged.Ok<>(LintContext.parseOnly(unit)), List.of())))
                .getFirst();
    }

    private List<FormatResult> skippingBlanks(final List<String> sources,
                                              final List<Set<String>> analyzeSkips,
                                              final BiFunction<List<String>, List<Set<String>>, List<FormatResult>> format) {
        final var formattable = IntStream.range(0, sources.size())
                .filter(i -> !sources.get(i).isEmpty())
                .boxed()
                .toList();
        final var results = formattable.isEmpty()
                ? List.<FormatResult>of()
                : format.apply(formattable.stream().map(sources::get).toList(), formattable.stream().map(analyzeSkips::get).toList());
        return scatter(sources.size(), formattable, results, i -> new FormatResult(sources.get(i), List.of()));
    }

    private List<FormatResult> formatParseOnly(final List<String> chunk, final JavafmtConfig config, final Renderer renderer) {
        return formatChunk(chunk, config, renderer, sources -> parseOnly(sources, List.of()));
    }

    private List<FormatResult> formatAttributed(final List<String> sources,
                                                final List<Set<String>> analyzeSkips,
                                                final JavafmtConfig config,
                                                final String classpath,
                                                final Renderer renderer) {
        return retriedWithoutClasspath(
                sources,
                JavaParser.attributing(
                        classpath, session -> formatChunk(sources, analyzeSkips, config, renderer, chunk -> attributedContexts(chunk, session))),
                config, renderer);
    }

    List<FormatResult> retriedWithoutClasspath(final List<String> sources,
                                               final List<FormatResult> results,
                                               final JavafmtConfig config,
                                               final Renderer renderer) {
        final var failed = IntStream.range(0, results.size())
                .filter(i -> results.get(i).diagnostics().stream().anyMatch(Diagnostic.InternalError.class::isInstance))
                .boxed()
                .toList();
        if (failed.isEmpty()) {
            return results;
        }
        final var retried = formatParseOnly(failed.stream().map(sources::get).toList(), config, renderer);
        return scatter(
                results.size(), failed,
                IntStream.range(0, failed.size())
                        .mapToObj(k -> FormatStages.withDiagnostics(retried.get(k), List.of(FormatStages.classpathFailureWarning(results.get(failed.get(k))))))
                        .toList(),
                results::get);
    }

    List<FormatResult> formatChunk(final List<String> chunk,
                                   final JavafmtConfig config,
                                   final Renderer renderer,
                                   final Function<List<String>, List<Prepared>> parser) {
        return formatChunk(chunk, chunk.stream().map(_ -> Set.<String>of()).toList(), config, renderer, parser);
    }

    /** Parses, lints, and renders one chunk of non-blank sources. */
    private List<FormatResult> formatChunk(final List<String> chunk,
                                           final List<Set<String>> analyzeSkips,
                                           final JavafmtConfig config,
                                           final Renderer renderer,
                                           final Function<List<String>, List<Prepared>> parser) {
        // Everything downstream works in LF with no BOM; how each file was written is put back on
        // its own result, so a CRLF file does not come out as a whole-file diff.
        final var encodings = chunk.stream().map(SourceEncoding::of).toList();
        final var sources = IntStream.range(0, chunk.size())
                .mapToObj(k -> encodings.get(k).normalize(chunk.get(k)))
                .toList();
        final var formatted = formatNormalized(sources, analyzeSkips, config, renderer, parser);
        return IntStream.range(0, formatted.size())
                .mapToObj(k -> FormatStages.asWritten(encodings.get(k), formatted.get(k)))
                .toList();
    }

    /**
     * The parse and lint stages run across the whole chunk, so a failure in either has no one
     * file to blame and would otherwise end the run. When that happens each file is formatted
     * again as a chunk of its own with the same {@code parser}, so an attributed retry keeps
     * its attribution: the files that are fine still come back formatted, and whatever failed
     * reappears as the diagnostic of the one file that caused it.
     */
    private List<FormatResult> formatNormalized(final List<String> sources,
                                                final List<Set<String>> analyzeSkips,
                                                final JavafmtConfig config,
                                                final Renderer renderer,
                                                final Function<List<String>, List<Prepared>> parser) {
        try {
            final var parsed = parser.apply(sources);
            final var settled = settled(
                    new Settled(
                            sources, lint(parsed, IntStream.range(0, sources.size()).boxed().toList(), analyzeSkips, config),
                            parsed.stream().map(Prepared::warnings).toList(), parsed.stream().map(BatchFormatter::isAttributed).toList()),
                    analyzeSkips, config, parser, 1);
            final var rendered = render(sources, settled.linted(), config, renderer);
            return IntStream.range(0, rendered.size())
                    .mapToObj(k -> FormatStages.withDiagnostics(rendered.get(k), settled.warnings().get(k)))
                    .toList();
        } catch (final RuntimeException | AssertionError | StackOverflowError batchFailure) {
            if (sources.size() == 1) {
                return List.of(FormatStages.internalErrorResult(sources.getFirst(), batchFailure));
            }
            return IntStream.range(0, sources.size())
                    .boxed()
                    .flatMap(i -> formatNormalized(List.of(sources.get(i)), List.of(analyzeSkips.get(i)), config, renderer, parser).stream())
                    .toList();
        }
    }

    /** Lints every successfully parsed source in one batch, leaving parse failures untouched. */
    private Settled settled(final Settled previous,
                            final List<Set<String>> analyzeSkips,
                            final JavafmtConfig config,
                            final Function<List<String>, List<Prepared>> parser,
                            final int pass) {
        final var changed = previous.changed();
        if (changed.isEmpty()) {
            return previous;
        }
        if (pass == MAX_ATTRIBUTED_PASSES) {
            return previous.withWarning(changed, FormatStages.attributedNonConvergenceWarning(MAX_ATTRIBUTED_PASSES));
        }
        final var current = previous.current();
        final var parsed = parser.apply(current);
        return settled(previous.next(current, changed, lint(parsed, changed, analyzeSkips, config), parsed), analyzeSkips, config, parser, pass + 1);
    }

    private List<Staged<LintEngine.Outcome>> lint(final List<Prepared> parsed,
                                                  final List<Integer> indices,
                                                  final List<Set<String>> analyzeSkips,
                                                  final JavafmtConfig config) {
        return lintBatch(
                indices.stream()
                        .map(i -> parsed.get(i).context().map(context -> context.skippingAnalysisFor(analyzeSkips.get(i))))
                        .toList(),
                config);
    }

    private boolean isAttributed(final Prepared prepared) {
        return prepared.context() instanceof Staged.Ok<LintContext>(var context) && context.attribution().isPresent();
    }

    private List<Staged<LintEngine.Outcome>> lintBatch(final List<Staged<LintContext>> parsed, final JavafmtConfig config) {
        return Staged.mapAll(parsed, contexts -> FormatStages.lintEngine(config).lintAll(targeting(contexts, config.release())));
    }

    private List<LintContext> targeting(final List<LintContext> contexts, final int release) {
        return contexts.stream().map(context -> context.withRelease(release)).toList();
    }

    private List<FormatResult> render(final List<String> sources,
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
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return onFailure.apply(e);
        } catch (final ExecutionException e) {
            final var cause = Optional.ofNullable(e.getCause()).orElse(e);
            if (cause instanceof OutOfMemoryError outOfMemory) {
                throw outOfMemory;
            }
            return onFailure.apply(cause);
        }
    }

    private List<Prepared> parseOnly(final List<String> chunk, final List<Diagnostic> warnings) {
        return JavaParser.parseUnits(chunk)
                .stream()
                .map(parsed -> new Prepared(parsed.map(LintContext::parseOnly), warnings))
                .toList();
    }

    /**
     * Attributes the whole chunk in one task. A file attribution fails for, or the whole chunk when
     * attribution fails outright (a bad classpath, say), is formatted parse-only instead, carrying a
     * warning so the degradation reaches the user rather than looking like a successful attributed
     * pass.
     */
    List<Prepared> attributedContexts(final List<String> chunk, final AttributionSession session) {
        try {
            final var unattributed = JavaParser.parseUnits(chunk);
            final var attributable = IntStream.range(0, chunk.size())
                    .filter(i -> !declaresAModule(unattributed.get(i)))
                    .boxed()
                    .toList();
            final var attributed = attributable.isEmpty()
                    ? List.<Staged<AttributedUnit>>of()
                    : session.attribute(attributable.stream().map(chunk::get).toList());
            final var byIndex = IntStream.range(0, attributable.size())
                    .boxed()
                    .collect(Collectors.toMap(attributable::get, attributed::get));
            return IntStream.range(0, chunk.size())
                    .mapToObj(
                            i -> Optional.ofNullable(byIndex.get(i))
                                    .map(outcome -> prepared(chunk.get(i), outcome, unattributed.get(i)))
                                    .orElseGet(() -> new Prepared(unattributed.get(i).map(LintContext::parseOnly), List.of())))
                    .toList();
        } catch (final RuntimeException e) {
            return parseOnly(chunk, List.of(FormatStages.attributionWarning(e)));
        }
    }

    private boolean javacStoppedOnAnUnresolvedName(final Throwable crash) {
        return Stream.iterate(crash, Objects::nonNull, Throwable::getCause).anyMatch(cause -> cause.getClass().getName().equals(JAVAC_FATAL_ERROR));
    }

    private boolean declaresAModule(final Staged<ParsedUnit> unit) {
        return unit instanceof Staged.Ok<ParsedUnit>(var parsed) && parsed.tree().getModule() != null;
    }

    List<Prepared> fromAttributed(final List<String> chunk, final List<Staged<AttributedUnit>> outcomes) {
        final var unattributed = JavaParser.parseUnits(chunk);
        return IntStream.range(0, chunk.size())
                .mapToObj(i -> prepared(chunk.get(i), outcomes.get(i), unattributed.get(i)))
                .toList();
    }

    private Prepared prepared(final String source, final Staged<AttributedUnit> outcome, final Staged<ParsedUnit> unattributed) {
        return switch (outcome) {
            case Staged.Ok<AttributedUnit>(var unit) -> new Prepared(unattributed.map(plain -> LintContext.attributed(unit, plain)), List.of());
            case Staged.Failed<AttributedUnit>(ParseException parseError) -> new Prepared(new Staged.Failed<>(parseError), List.of());
            case Staged.Failed<AttributedUnit>(var crash) ->
                parseOnly(List.of(source), javacStoppedOnAnUnresolvedName(crash) ? List.of() : List.of(FormatStages.attributionWarning(crash)))
                        .getFirst();
        };
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

    private record Settled(List<String> inputs, List<Staged<LintEngine.Outcome>> linted, List<List<Diagnostic>> warnings, List<Boolean> attributed) {
        List<Integer> changed() {
            return IntStream.range(0, inputs.size())
                    .filter(
                            i -> attributed.get(i)
                                && linted.get(i) instanceof Staged.Ok<LintEngine.Outcome>(var outcome)
                                && !outcome.unit().source().equals(inputs.get(i)))
                    .boxed()
                    .toList();
        }

        List<String> current() {
            return IntStream.range(0, inputs.size())
                    .mapToObj(i -> linted.get(i) instanceof Staged.Ok<LintEngine.Outcome>(var outcome) ? outcome.unit().source() : inputs.get(i))
                    .toList();
        }

        Settled next(final List<String> current,
                     final List<Integer> changed,
                     final List<Staged<LintEngine.Outcome>> relinted,
                     final List<Prepared> parsed) {
            final var position = IntStream.range(0, changed.size()).boxed().collect(Collectors.toMap(changed::get, k -> k));
            return new Settled(
                    current,
                    IntStream.range(0, inputs.size())
                            .mapToObj(i -> Optional.ofNullable(position.get(i)).map(relinted::get).orElseGet(() -> linted.get(i)))
                            .toList(),
                    IntStream.range(0, inputs.size())
                            .mapToObj(i -> position.containsKey(i) ? parsed.get(i).warnings() : warnings.get(i))
                            .toList(),
                    IntStream.range(0, inputs.size())
                            .mapToObj(i -> position.containsKey(i) ? isAttributed(parsed.get(i)) : attributed.get(i))
                            .toList());
        }

        Settled withWarning(final List<Integer> changed, final Diagnostic warning) {
            return new Settled(
                    inputs, linted,
                    IntStream.range(0, inputs.size())
                            .mapToObj(
                                    i -> changed.contains(i) ? Stream.concat(warnings.get(i).stream(), Stream.of(warning)).toList() : warnings.get(i))
                            .toList(),
                    attributed);
        }
    }

    private sealed interface FileRead {
        record Pending(Path path, String source, Set<String> analyzeSkip) implements FileRead {}

        record Done(FileResult result) implements FileRead {}
    }

    @FunctionalInterface
    interface Renderer {
        Renderer INLINE = (_, tasks) -> tasks.stream().map(Supplier::get).toList();

        List<FormatResult> renderAll(List<String> sources, List<Supplier<FormatResult>> tasks);

        static Renderer on(final Executor pool) {
            return (sources, tasks) -> fanOut(pool, tasks, (i, failure) -> FormatStages.internalErrorResult(sources.get(i), failure));
        }
    }
}
