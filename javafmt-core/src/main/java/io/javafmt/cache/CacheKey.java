package io.javafmt.cache;

import io.javafmt.JavafmtConfig;
import java.util.Objects;

/**
 * The part of a run's configuration that decides what a formatted file looks like.
 *
 * <p>A cache written under one configuration must not be read under another. Without this, a plain
 * run followed by an {@code --analyze} run finds every file up to date, skips it, and the
 * analyze-only rewrites never happen — which looks from the outside like javafmt doing nothing.
 */
public record CacheKey(boolean lint, boolean verifyOutput, boolean analyze, String classpath) {

    public CacheKey {
        Objects.requireNonNull(classpath, "classpath");
    }

    /** The key for a run under {@code config}. An {@code --analyze} run also keys on its classpath. */
    public static CacheKey of(final JavafmtConfig config) {
        Objects.requireNonNull(config, "config");
        return switch (config.analysis()) {
            case JavafmtConfig.Analysis.ParseOnly() -> new CacheKey(config.lint(), config.verifyOutput(), false, "");
            case JavafmtConfig.Analysis.Attributed(var classpath) -> new CacheKey(config.lint(), config.verifyOutput(), true, classpath);
        };
    }

    /** A short stable digest of this key, for the cache file's header line. */
    public String fingerprint() {
        final var material = "lint=" + lint + " verify=" + verifyOutput + " analyze=" + analyze + " classpath=" + classpath;
        return FileFormatCache.sha256(material).substring(0, 16);
    }
}
