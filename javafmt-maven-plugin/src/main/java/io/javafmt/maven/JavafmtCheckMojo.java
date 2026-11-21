package io.javafmt.maven;

import io.javafmt.FileResult;
import io.javafmt.cache.FormatCache;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class JavafmtCheckMojo extends AbstractJavafmtMojo {

    public JavafmtCheckMojo() {}

    JavafmtCheckMojo(final MavenProject project, final List<String> excludes, final Optional<Path> cacheFile) {
        this(project, excludes, cacheFile, Optional.empty());
    }

    JavafmtCheckMojo(final MavenProject project, final List<String> excludes, final Optional<Path> cacheFile, final Optional<String> ratchetFrom) {
        super(project, excludes, cacheFile, ratchetFrom);
    }

    @Override
    void handle(final List<FileResult.Formatted> formatted, final FormatCache formatCache) throws MojoExecutionException {
        formatted.stream()
                .filter(file -> !file.changed())
                .forEach(file -> formatCache.recordFormatted(file.path(), file.source()));
        final var unformatted = formatted.stream()
                .filter(FileResult.Formatted::changed)
                .map(FileResult.Formatted::path)
                .toList();
        if (!unformatted.isEmpty()) {
            final var fileList = unformatted.stream()
                    .map(file -> "  " + file)
                    .collect(Collectors.joining("\n"));
            throw new MojoExecutionException("javafmt check failed: " + unformatted.size() + " file(s) not formatted:\n" + fileList);
        }
    }
}
