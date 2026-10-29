package io.javafmt.cache;

import io.javafmt.JavafmtConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The part of a run's configuration that decides what a formatted file looks like.
 *
 * <p>A cache written under one configuration must not be read under another. Without this, a plain
 * run followed by an {@code --analyze} run finds every file up to date, skips it, and the
 * analyze-only rewrites never happen — which looks from the outside like javafmt doing nothing.
 *
 * <p>{@link JavafmtConfig#excludes()} is deliberately left out: it decides which files a run
 * visits, not what any one of them formats to.
 */
public record CacheKey(boolean lint, boolean verifyOutput, boolean analyze, String classpath) {

    public CacheKey {
        Objects.requireNonNull(classpath, "classpath");
    }

    /** The key for a run that formats without resolved types. */
    public static CacheKey of(final JavafmtConfig config) {
        Objects.requireNonNull(config, "config");
        return new CacheKey(config.lint(), config.verifyOutput(), false, "");
    }

    /** The key for an {@code --analyze} run, which also depends on the classpath it resolves against. */
    public static CacheKey ofAnalysis(final JavafmtConfig config, final String classpath) {
        Objects.requireNonNull(config, "config");
        return new CacheKey(config.lint(), config.verifyOutput(), true, classpath);
    }

    /** A short stable digest of this key, for the cache file's header line. */
    public String fingerprint() {
        final var material = "lint=" + lint + " verify=" + verifyOutput + " analyze=" + analyze + " classpath=" + classpath;
        return HexFormat.of()
                .formatHex(digest().digest(material.getBytes(StandardCharsets.UTF_8)))
                .substring(0, 16);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
