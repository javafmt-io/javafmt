package io.javafmt.maven;

import io.javafmt.FileResult;
import io.javafmt.cache.FormatCache;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class JavafmtCheckMojo extends AbstractJavafmtMojo {

    public JavafmtCheckMojo() {}

    JavafmtCheckMojo(final MavenProject project, final List<String> excludes, final Optional<Path> cacheFile) {
        super(project, excludes, cacheFile);
    }

    @Override
    void handle(final List<FileResult.Formatted> formatted, final FormatCache formatCache) throws MojoExecutionException {
        final var unformatted = formatted.stream()
                .flatMap(file -> checkResult(file, formatCache).stream())
                .toList();
        if (!unformatted.isEmpty()) {
            final var fileList = unformatted.stream().map(file -> "  " + file).collect(Collectors.joining("\n"));
            throw new MojoExecutionException("javafmt check failed: " + unformatted.size() + " file(s) not formatted:\n" + fileList);
        }
    }

    private Optional<Path> checkResult(final FileResult.Formatted file, final FormatCache formatCache) {
        if (file.changed()) {
            return Optional.of(file.path());
        }
        formatCache.recordFormatted(file.path(), file.source());
        return Optional.empty();
    }
}
