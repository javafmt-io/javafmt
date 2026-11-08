package io.javafmt.cache;

import io.javafmt.JavafmtConfig;
import io.javafmt.ProjectFile;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The part of a run's configuration that decides what a formatted file looks like.
 *
 * <p>A cache written under one configuration must not be read under another. Without this, a plain
 * run followed by one with a classpath finds every file up to date, skips it, and the rewrites
 * that need the classpath never happen — which looks from the outside like javafmt doing nothing.
 * The project files that govern the run's sources are part of it too, since {@code analyze.skip}
 * turns some of those rewrites off.
 */
public record CacheKey(boolean lint, boolean verifyOutput, boolean analyze, String classpath, int release, List<String> projectFiles) {

    public CacheKey {
        Objects.requireNonNull(classpath, "classpath");
        projectFiles = projectFiles.stream()
                .distinct()
                .sorted()
                .toList();
    }

    /** The key for a run under {@code config}. A run with a classpath also keys on it. */
    public static CacheKey of(final JavafmtConfig config) {
        return of(config, List.of());
    }

    public static CacheKey of(final JavafmtConfig config, final Collection<ProjectFile> projectFiles) {
        Objects.requireNonNull(config, "config");
        final var described = projectFiles.stream()
                .flatMap(projectFile -> describe(projectFile).stream())
                .toList();
        return switch (config.analysis()) {
            case JavafmtConfig.Analysis.ParseOnly() -> new CacheKey(config.lint(), config.verifyOutput(), false, "", config.release(), described);
            case JavafmtConfig.Analysis.Attributed(var classpath) ->
                new CacheKey(config.lint(), config.verifyOutput(), true, classpath, config.release(), described);
        };
    }

    private static List<String> describe(final ProjectFile projectFile) {
        return switch (projectFile) {
            case ProjectFile.None() -> List.of();
            case ProjectFile.Settings(var location, var analyzeSkip) -> List.of(
                    location + " " + ProjectFile.ANALYZE_SKIP + "="
                            + analyzeSkip.stream()
                                    .sorted()
                                    .collect(Collectors.joining(",")));
            case ProjectFile.Invalid(var location, var problem) -> List.of(location + " invalid: " + problem);
        };
    }

    /** A short stable digest of this key, for the cache file's header line. */
    public String fingerprint() {
        final var material = "lint=" + lint + " verify=" + verifyOutput + " analyze=" + analyze + " classpath=" + classpath + " release=" + release
                + (projectFiles.isEmpty() ? "" : " projectFiles=" + String.join("\n", projectFiles));
        return FileFormatCache.sha256(material).substring(0, 16);
    }
}
