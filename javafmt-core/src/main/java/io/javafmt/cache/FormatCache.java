package io.javafmt.cache;

import java.nio.file.Path;

/**
 * Incremental-formatting cache: maps file paths to a hash of their last-known post-format
 * content. A file is "up to date" when its current content hashes to the same value as the
 * cache entry — i.e. it would format to itself, so we can skip running the formatter.
 *
 * <p>The cache is opt-in. Default integrations should use {@link #noop()}, which records
 * nothing and treats every file as needing formatting.</p>
 */
public interface FormatCache {

    /**
     * Returns {@code true} if {@code currentContent} matches the cached post-format hash for
     * {@code file}. When this returns true, the caller may safely skip formatting.
     */
    boolean isUpToDate(Path file, String currentContent);

    /**
     * Records that {@code file} now contains {@code postFormatContent} after a successful
     * format. Subsequent calls to {@link #isUpToDate(Path, String)} with the same content
     * must return {@code true}.
     */
    void recordFormatted(Path file, String postFormatContent);

    /**
     * Persists the cache. No-op for in-memory or disabled implementations.
     */
    void save();

    /**
     * A cache that records nothing and reports every file as needing formatting. Use this
     * when caching is disabled.
     */
    static FormatCache noop() {
        return NoopFormatCache.INSTANCE;
    }
}
