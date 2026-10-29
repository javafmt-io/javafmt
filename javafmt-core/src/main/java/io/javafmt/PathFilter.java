package io.javafmt;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Exclude-glob filtering shared by every integration. A path is excluded when its full path OR
 * its bare filename matches at least one glob; {@code **} crosses directory boundaries, {@code *}
 * does not ({@link java.nio.file.FileSystem#getPathMatcher "glob:..."} semantics).
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PathFilter {

    /**
     * Directory names a directory argument is never searched into: build output, dependency
     * downloads and generated sources, all of which are rewritten by the tool that produced them,
     * so formatting them is churn. Matched on the path below the root the caller named, never on
     * the root itself, so {@code javafmt build} still formats what is inside {@code build}.
     */
    public static final Set<String> DEFAULT_EXCLUDED_DIRECTORIES = Set.of(".git", "build", "generated", "node_modules", "out", "target");

    private final List<PathMatcher> matchers;

    public static PathFilter ofGlobs(final List<String> globs) {
        Objects.requireNonNull(globs, "globs");
        return new PathFilter(globs.stream().map(g -> FileSystems.getDefault().getPathMatcher("glob:" + g)).toList());
    }

    public boolean excludes(final Path path) {
        final var fileName = path.getFileName();
        return matchers.stream().anyMatch(m -> m.matches(path) || (fileName != null && m.matches(fileName)));
    }

    /** Whether {@code directory} is named as one of {@link #DEFAULT_EXCLUDED_DIRECTORIES}. */
    public static boolean isDefaultExcludedDirectory(final Path directory) {
        Objects.requireNonNull(directory, "directory");
        final var name = directory.getFileName();
        return name != null && DEFAULT_EXCLUDED_DIRECTORIES.contains(name.toString());
    }

    public List<Path> keep(final List<Path> paths) {
        return paths.stream().filter(p -> !excludes(p)).toList();
    }
}
