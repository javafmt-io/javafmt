package io.javafmt.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.javafmt.Javafmt;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class JavafmtCheckMojoTest {

    private static final String UNFORMATTED = "class   Foo{\nvoid bar()  { }\n}\n";

    @Test
    void checkGoalPassesWhenAllFilesFormatted(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, Javafmt.format(UNFORMATTED), StandardCharsets.UTF_8);

        final var mojo = new JavafmtCheckMojo(buildProject(srcDir));
        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void checkGoalFailsWithUnformattedFilesNamed(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Unformatted.java");
        Files.writeString(file, UNFORMATTED, StandardCharsets.UTF_8);

        final var mojo = new JavafmtCheckMojo(buildProject(srcDir));
        assertThatThrownBy(mojo::execute)
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("javafmt check failed: 1 file(s) not formatted")
            .hasMessageContaining(file.toString());
    }

    @Test
    void checkGoalSkipsParseErrorFilesWithoutFailing(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Bad.java");
        Files.writeString(file, "this is not valid java source code", StandardCharsets.UTF_8);

        final var mojo = new JavafmtCheckMojo(buildProject(srcDir));
        assertThatNoException().isThrownBy(mojo::execute);
    }

    private static MavenProject buildProject(final Path srcDir) {
        final var build = new Build();
        build.setSourceDirectory(srcDir.toAbsolutePath().toString());
        build.setTestSourceDirectory(srcDir.toAbsolutePath().toString() + "-test");
        final var project = new MavenProject();
        project.getModel().setBuild(build);
        return project;
    }
}
