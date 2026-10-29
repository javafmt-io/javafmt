package io.javafmt.maven;

import io.javafmt.FormatResult;
import io.javafmt.cache.FormatCache;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class JavafmtCheckMojo extends AbstractJavafmtMojo {

    public JavafmtCheckMojo() {}

    JavafmtCheckMojo(final MavenProject project) {
        super(project);
    }

    JavafmtCheckMojo(final MavenProject project, final List<String> excludes) {
        super(project, excludes);
    }

    JavafmtCheckMojo(final MavenProject project, final boolean cache, final Path cacheFile) {
        super(project, cache, cacheFile);
    }

    @Override
    void handle(final List<FileWork> work, final FormatCache formatCache) throws MojoExecutionException {
        final var unformatted = work.stream()
                .flatMap(
                        w -> switch (w) {
                            case FileWork.Skipped ignored -> Stream.empty();
                            case FileWork.Formatted(var file, var content, var result) -> checkResult(file, content, result, formatCache).stream();
                        })
                .toList();
        if (!unformatted.isEmpty()) {
            final var fileList = unformatted.stream().map(file -> "  " + file).collect(Collectors.joining("\n"));
            throw new MojoExecutionException("javafmt check failed: " + unformatted.size() + " file(s) not formatted:\n" + fileList);
        }
    }

    private Optional<Path> checkResult(final Path file, final String content, final FormatResult result, final FormatCache formatCache) {
        final var errors = result.errors();
        if (!errors.isEmpty()) {
            errors.forEach(d -> getLog().warn("javafmt: " + d.describe(file.toString())));
            return Optional.empty();
        }
        if (!result.output().equals(content)) {
            return Optional.of(file);
        }
        formatCache.recordFormatted(file, content);
        return Optional.empty();
    }
}
