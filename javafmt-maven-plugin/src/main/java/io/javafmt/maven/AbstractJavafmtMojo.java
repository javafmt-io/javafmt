package io.javafmt.maven;

import io.javafmt.FormatResult;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.PathFilter;
import io.javafmt.SourceFiles;
import io.javafmt.cache.CacheKey;
import io.javafmt.cache.FileFormatCache;
import io.javafmt.cache.FormatCache;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
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

    AbstractJavafmtMojo(final MavenProject project) {
        this(project, List.of());
    }

    AbstractJavafmtMojo(final MavenProject project, final List<String> excludes) {
        this.project = project;
        this.excludes = List.copyOf(excludes);
    }

    AbstractJavafmtMojo(final MavenProject project, final boolean cache, final Path cacheFile) {
        this.project = project;
        this.cache = cache;
        this.cacheFile = cacheFile.toString();
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
            handle(formatBatch(files, formatCache), formatCache);
            formatCache.save();
        } catch (UncheckedIOException e) {
            throw new MojoExecutionException("javafmt: I/O error: " + e.getMessage(), e.getCause());
        }
    }

    /** What the goal does with the batch results; throws MojoExecutionException to fail the build. */
    abstract void handle(List<FileWork> work, FormatCache formatCache) throws MojoExecutionException;

    private FormatCache resolveCache(final MavenProject p) {
        if (!cache) {
            return FormatCache.noop();
        }
        final var path = cacheFile != null ? Path.of(cacheFile) : Path.of(p.getBuild().getDirectory(), "javafmt.cache");
        return FileFormatCache.load(path, CacheKey.of(JavafmtConfig.defaults()));
    }

    /**
     * Reads every file, then formats the ones that need it in one batch. The mojo has the whole
     * file list up front, so it can use {@link Javafmt#formatAll} and share javac task setup
     * across the module instead of paying it per file.
     *
     * <p>Cache hits are formatted as {@code ""} so they never reach javac, while {@code cacheHits}
     * tracks them separately so an actually-empty source file is still processed correctly.
     */
    private List<FileWork> formatBatch(final List<Path> files, final FormatCache formatCache) {
        final var poolSize = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
        final var contents = files.stream().map(AbstractJavafmtMojo::read).toList();
        final var cacheHits = IntStream.range(0, files.size())
                .mapToObj(i -> formatCache.isUpToDate(files.get(i), contents.get(i)))
                .toList();
        final var toFormat = IntStream.range(0, files.size())
                .mapToObj(i -> cacheHits.get(i) ? "" : contents.get(i))
                .toList();
        final var results = Javafmt.formatAll(toFormat, JavafmtConfig.defaults(), poolSize);
        return IntStream.range(0, files.size())
                .<FileWork>mapToObj(
                        i -> cacheHits.get(i)
                                ? new FileWork.Skipped(files.get(i))
                                : new FileWork.Formatted(files.get(i), contents.get(i), results.get(i)))
                .toList();
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    private static List<Path> walkNullable(@Nullable final String dir) {
        return dir == null ? List.of() : SourceFiles.walk(Path.of(dir));
    }

    sealed interface FileWork {

        record Skipped(Path path) implements FileWork {}

        record Formatted(Path path, String content, FormatResult result) implements FileWork {}
    }
}
