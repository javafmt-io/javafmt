package io.javafmt;

import io.javafmt.cache.FormatCache;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * The entry point for formatting Java source with javafmt. {@link #format} and
 * {@link #formatWithResult} format one source; use {@link #formatAll} to format many sources at
 * once and share javac task setup across them.
 *
 * <p>Formatting never throws for input that fails to parse or that javafmt itself chokes on:
 * every overload returns a result (either the formatted text directly, via {@link #format}, or
 * a {@link FormatResult} carrying diagnostics) and, on failure, hands back the original source
 * unchanged with the failure recorded as a {@link Diagnostic}.
 */
@UtilityClass
public final class Javafmt {

    /** {@link #format(String)} with an explicit configuration. */
    public String format(final String source, final JavafmtConfig config) {
        return formatWithResult(source, config).output();
    }

    /**
     * Formats {@code source} and returns the formatted text. On failure, returns {@code source}
     * unchanged; use {@link #formatWithResult(String)} if the caller needs to know why.
     */
    public String format(final String source) {
        return format(source, JavafmtConfig.defaults());
    }

    /** {@link #formatWithResult(String, JavafmtConfig)} with the default configuration. */
    public FormatResult formatWithResult(final String source) {
        return formatWithResult(source, JavafmtConfig.defaults());
    }

    /** Formats {@code source}, returning both the output and any diagnostics raised. */
    public FormatResult formatWithResult(final String source, final JavafmtConfig config) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(config, "config");
        return BatchFormatter.formatOne(source, config);
    }

    /**
     * Formats many sources at once, sharing javac task setup across them. Result {@code i}
     * corresponds to {@code sources.get(i)}. Parse-only, it is identical to what
     * {@link #format(String, JavafmtConfig)} returns for that source alone — only the cost differs.
     * Attributed, names declared in other sources of the run resolve too, which they cannot for a
     * source formatted alone.
     *
     * <p>Prefer this over a loop whenever the whole file list is known up front. Task setup is
     * ~98% of a single-file parse, and the lint pass re-parses each file it rewrites, so a
     * per-file loop pays that setup several times per file. Batching a dirty corpus measured ~4x
     * faster than formatting the same files one at a time.
     *
     * <p>Sources are processed in chunks, one per thread at a time, so peak memory stays bounded by
     * {@code threads} chunks' syntax trees rather than the whole run. Blank sources are passed
     * straight through without occupying a parse slot, which lets callers keep placeholder entries
     * for files they resolved some other way (a cache hit, or a read error) and stay index-aligned.
     *
     * <p>With {@link JavafmtConfig.Analysis.Attributed} the run does <em>not</em> chunk: every
     * source is attributed in one task so that cross-file references resolve. Splitting the run
     * would leave a type declared in one chunk unresolvable from another, and rules that key on
     * resolved symbols — {@code ExplodeStarImports} in particular — deliberately do nothing when a
     * name will not resolve. Peak memory is therefore proportional to the whole run.
     *
     * @param threads how many chunks format in parallel; values below 1 are treated as 1
     */
    public List<FormatResult> formatAll(final List<String> sources, final JavafmtConfig config, final int threads) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(config, "config");
        return BatchFormatter.formatAll(sources, config, threads);
    }

    public List<FileResult> formatFiles(final List<Path> paths, final JavafmtConfig config, final FormatCache cache, final int threads) {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(cache, "cache");
        return BatchFormatter.formatFiles(paths, config, cache, threads);
    }
}
