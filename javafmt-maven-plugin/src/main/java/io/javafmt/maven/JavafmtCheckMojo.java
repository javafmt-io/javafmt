package io.javafmt.maven;

import io.javafmt.Diagnostic;
import io.javafmt.Javafmt;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class JavafmtCheckMojo extends AbstractMojo {

    @Nullable
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    public JavafmtCheckMojo() {}

    JavafmtCheckMojo(final MavenProject project) {
        this.project = project;
    }

    @Override
    public void execute() throws MojoExecutionException {
        final var p = Objects.requireNonNull(project, "project");
        final var build = p.getBuild();
        final var files = Stream.concat(
            walkDir(build.getSourceDirectory()).stream(),
            walkDir(build.getTestSourceDirectory()).stream()
        ).toList();

        try {
            final var unformatted = files.stream()
                .flatMap(file -> checkFile(file).stream())
                .toList();
            if (!unformatted.isEmpty()) {
                final var fileList = unformatted.stream()
                    .map(file -> "  " + file)
                    .collect(Collectors.joining("\n"));
                throw new MojoExecutionException(
                    "javafmt check failed: " + unformatted.size() + " file(s) not formatted:\n" + fileList
                );
            }
        } catch (final UncheckedIOException e) {
            throw new MojoExecutionException("javafmt: I/O error: " + e.getMessage(), e.getCause());
        }
    }

    private Optional<Path> checkFile(final Path file) {
        final String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }

        final var result = Javafmt.formatWithResult(content);

        if (result.diagnostics().stream().anyMatch(d -> d instanceof Diagnostic.ParseError)) {
            result.diagnostics().stream()
                .filter(d -> d instanceof Diagnostic.ParseError)
                .forEach(d -> getLog().warn("javafmt: parse error in " + file + ": " + d.message()));
            return Optional.empty();
        }

        if (!result.output().equals(content)) {
            return Optional.of(file);
        }

        return Optional.empty();
    }

    private List<Path> walkDir(final @Nullable String dir) throws MojoExecutionException {
        if (dir == null) {
            return List.of();
        }
        final var path = Path.of(dir);
        if (!Files.isDirectory(path)) {
            return List.of();
        }
        try (final var walk = Files.walk(path)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (final IOException e) {
            throw new MojoExecutionException("Failed to walk " + dir, e);
        }
    }
}
