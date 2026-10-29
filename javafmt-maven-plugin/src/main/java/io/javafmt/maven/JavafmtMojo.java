package io.javafmt.maven;

import io.javafmt.FormatResult;
import io.javafmt.SourceFiles;
import io.javafmt.cache.FormatCache;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

@Mojo(name = "format", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public final class JavafmtMojo extends AbstractJavafmtMojo {

    public JavafmtMojo() {}

    JavafmtMojo(final MavenProject project) {
        super(project);
    }

    JavafmtMojo(final MavenProject project, final List<String> excludes) {
        super(project, excludes);
    }

    JavafmtMojo(final MavenProject project, final boolean cache, final Path cacheFile) {
        super(project, cache, cacheFile);
    }

    @Override
    void handle(final List<FileWork> work, final FormatCache formatCache) {
        final var results = work.stream()
                .map(
                        w -> switch (w) {
                            case FileWork.Skipped ignored -> new FileResult(false, 0);
                            case FileWork.Formatted(var file, var content, var result) -> processResult(file, content, result, formatCache);
                        })
                .toList();
        final var formattedCount = results.stream().filter(FileResult::changed).count();
        final var warningCount = results.stream().mapToLong(FileResult::warnings).sum();
        getLog().info("javafmt: formatted " + formattedCount + " files, " + warningCount + " warnings");
    }

    private FileResult processResult(final Path file, final String content, final FormatResult result, final FormatCache formatCache) {
        final var errors = result.errors();
        if (!errors.isEmpty()) {
            errors.forEach(d -> getLog().warn("javafmt: " + d.describe(file.toString())));
            return new FileResult(false, 0);
        }
        final var warnings = result.warnings();
        warnings.forEach(w -> getLog().info("javafmt: " + w.describe(file.toString())));
        if (!result.output().equals(content)) {
            SourceFiles.write(file, result.output());
            getLog().debug("javafmt: formatted " + file);
            formatCache.recordFormatted(file, result.output());
            return new FileResult(true, warnings.size());
        }
        formatCache.recordFormatted(file, content);
        return new FileResult(false, warnings.size());
    }

    private record FileResult(boolean changed, int warnings) {}
}
