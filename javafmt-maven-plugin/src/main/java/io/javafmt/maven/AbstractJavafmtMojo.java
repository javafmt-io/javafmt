package io.javafmt.maven;

import io.javafmt.FileResult;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.PathFilter;
import io.javafmt.SourceFiles;
import io.javafmt.cache.CacheKey;
import io.javafmt.cache.FileFormatCache;
import io.javafmt.cache.FormatCache;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/** Template contract: {@link #execute()} is final; subclasses supply {@link #handle}. */
abstract class AbstractJavafmtMojo extends AbstractMojo {

    @Nullable
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * Maximum number of files to process concurrently. Defaults to the number of available
     * processors. Per-file IR build/print is single-threaded; parallelism is at the file
     * boundary.
     */
    @Parameter(property = "javafmt.threads", defaultValue = "0")
    private int threads;

    /**
     * Enable incremental processing. When {@code true}, javafmt keeps a small hash-based
     * cache of already-formatted files and skips files whose content matches the cached
     * hash. Default: {@code false}.
     */
    @Parameter(property = "javafmt.cache", defaultValue = "false")
    private boolean cache;

    /**
     * Cache file location. Defaults to {@code ${project.build.directory}/javafmt.cache}.
     * Only consulted when {@link #cache} is {@code true}.
     */
    @Nullable
    @Parameter(property = "javafmt.cacheFile")
    private String cacheFile;

    /**
     * ANT-style glob patterns (matched by {@link java.nio.file.FileSystem#getPathMatcher
     * "glob:..."}) of source paths to skip. Each entry is tested against both the full path
     * and the bare filename; a match in either drops the file.
     */
    @Parameter private List<String> excludes = List.of();

    AbstractJavafmtMojo() {}

    AbstractJavafmtMojo(final MavenProject project, final List<String> excludes, final Optional<Path> cacheFile) {
        this.project = project;
        this.excludes = List.copyOf(excludes);
        this.cache = cacheFile.isPresent();
        this.cacheFile = cacheFile.map(Path::toString).orElse(null);
    }

    @Override
    public final void execute() throws MojoExecutionException {
        final var p = Objects.requireNonNull(project, "project");
        final var build = p.getBuild();
        final var filter = PathFilter.ofGlobs(excludes);
        final var formatCache = resolveCache(p);
        try {
            final var files = filter.keep(
                    Stream.concat(walkNullable(build.getSourceDirectory()).stream(), walkNullable(build.getTestSourceDirectory()).stream())
                            .toList());
            final var poolSize = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
            final var results = Javafmt.formatFiles(files, JavafmtConfig.defaults(), formatCache, poolSize);
            failOnUnreadable(results);
            handle(logErrorsAndKeepCleanFiles(results), formatCache);
            formatCache.save();
        } catch (UncheckedIOException e) {
            throw new MojoExecutionException("javafmt: I/O error: " + e.getMessage(), e.getCause());
        }
    }

    /** What the goal does with the files that formatted without errors; throws MojoExecutionException to fail the build. */
    abstract void handle(List<FileResult.Formatted> formatted, FormatCache formatCache) throws MojoExecutionException;

    private static void failOnUnreadable(final List<FileResult> results) throws MojoExecutionException {
        final var unreadable = results.stream()
                .flatMap(result -> result instanceof FileResult.Unreadable u ? Stream.of(u) : Stream.empty())
                .findFirst();
        if (unreadable.isPresent()) {
            throw new MojoExecutionException("javafmt: I/O error: Failed to read " + unreadable.get().path(), unreadable.get().error());
        }
    }

    private List<FileResult.Formatted> logErrorsAndKeepCleanFiles(final List<FileResult> results) {
        final var formatted = results.stream()
                .flatMap(result -> result instanceof FileResult.Formatted f ? Stream.of(f) : Stream.empty())
                .toList();
        formatted.forEach(f -> f.result().errors().forEach(d -> getLog().warn("javafmt: " + d.describe(f.path().toString()))));
        return formatted.stream().filter(f -> !f.result().hasErrors()).toList();
    }

    private FormatCache resolveCache(final MavenProject p) {
        if (!cache) {
            return FormatCache.noop();
        }
        final var path = cacheFile != null ? Path.of(cacheFile) : Path.of(p.getBuild().getDirectory(), "javafmt.cache");
        return FileFormatCache.load(path, CacheKey.of(JavafmtConfig.defaults()));
    }

    private static List<Path> walkNullable(@Nullable final String dir) {
        return dir == null ? List.of() : SourceFiles.walk(Path.of(dir));
    }
}
