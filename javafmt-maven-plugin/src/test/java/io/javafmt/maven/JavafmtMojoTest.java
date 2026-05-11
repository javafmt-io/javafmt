package io.javafmt.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.javafmt.Javafmt;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class JavafmtMojoTest {

    private static final String UNFORMATTED = "class   Foo{\nvoid bar()  { }\n}\n";

    @Test
    void formatGoalRewritesUnformattedFile(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, UNFORMATTED, StandardCharsets.UTF_8);

        final var mojo = new JavafmtMojo(buildProject(srcDir));
        mojo.execute();

        assertThat(Files.readString(file, StandardCharsets.UTF_8))
            .isEqualTo(Javafmt.format(UNFORMATTED))
            .isNotEqualTo(UNFORMATTED);
    }

    @Test
    void formatGoalLeavesFormattedFileUnchanged(@TempDir final Path srcDir) throws Exception {
        final var formatted = Javafmt.format(UNFORMATTED);
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, formatted, StandardCharsets.UTF_8);

        final var mojo = new JavafmtMojo(buildProject(srcDir));
        mojo.execute();

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(formatted);
    }

    @Test
    void formatGoalSkipsFileWithParseError(@TempDir final Path srcDir) throws Exception {
        final var badContent = "this is not valid java source code";
        final var file = srcDir.resolve("Bad.java");
        Files.writeString(file, badContent, StandardCharsets.UTF_8);

        final var mojo = new JavafmtMojo(buildProject(srcDir));
        assertThatNoException().isThrownBy(mojo::execute);

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(badContent);
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
