package io.javafmt.maven;

import io.javafmt.FileResult;
import io.javafmt.SourceFiles;
import io.javafmt.cache.FormatCache;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

@Mojo(name = "format", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public final class JavafmtMojo extends AbstractJavafmtMojo {

    public JavafmtMojo() {}

    JavafmtMojo(final MavenProject project, final List<String> excludes, final Optional<Path> cacheFile) {
        super(project, excludes, cacheFile);
    }

    @Override
    void handle(final List<FileResult.Formatted> formatted, final FormatCache formatCache) {
        final var formattedCount = formatted.stream()
                .map(file -> processResult(file, formatCache))
                .filter(Boolean::booleanValue)
                .count();
        final var warningCount = formatted.stream().mapToLong(file -> file.result().warnings().size()).sum();
        getLog().info("javafmt: formatted " + formattedCount + " files, " + warningCount + " warnings");
    }

    private boolean processResult(final FileResult.Formatted file, final FormatCache formatCache) {
        final var result = file.result();
        result.warnings().forEach(w -> getLog().info("javafmt: " + w.describe(file.path().toString())));
        if (file.changed()) {
            SourceFiles.write(file.path(), result.output());
            getLog().debug("javafmt: formatted " + file.path());
        }
        formatCache.recordFormatted(file.path(), result.output());
        return file.changed();
    }
}
