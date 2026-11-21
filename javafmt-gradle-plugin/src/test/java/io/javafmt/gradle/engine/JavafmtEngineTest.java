package io.javafmt.gradle.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JavafmtEngineTest {

    private static final String UNFORMATTED = "class A { int x; }";

    private static final String FORMATTED = "class A {\n\n    int x;\n}\n";

    private static final String WARNED =
            "class Warned {\n\n    @Override\n    public boolean equals(final Object other) {\n        return this == other;\n    }\n}\n";

    @Test
    void reportsAnUnformattedFileAsChangedWithoutWritingIt(@TempDir final Path dir) throws IOException {
        final var file = write(dir, "src/A.java", UNFORMATTED);
        final var outcome = JavafmtEngine.run(request(dir, false, file));
        assertThat(outcome.changed())
                .containsExactly(new Outcome.Source(file.toAbsolutePath().toString(), "src/A.java".replace('/', File.separatorChar)));
        assertThat(outcome.errors()).isEmpty();
        assertThat(Files.readString(file)).isEqualTo(UNFORMATTED);
    }

    @Test
    void rewritesTheFileWhenAskedToWrite(@TempDir final Path dir) throws IOException {
        final var file = write(dir, "src/A.java", UNFORMATTED);
        final var outcome = JavafmtEngine.run(request(dir, true, file));
        assertThat(outcome.changed()).hasSize(1);
        assertThat(Files.readString(file)).isEqualTo(FORMATTED);
    }

    @Test
    void aFormattedFileIsNotChanged(@TempDir final Path dir) throws IOException {
        final var file = write(dir, "src/A.java", FORMATTED);
        final var outcome = JavafmtEngine.run(request(dir, false, file));
        assertThat(outcome).isEqualTo(new Outcome(List.of(), List.of(), List.of()));
    }

    @Test
    void reportsASyntaxErrorWithItsLineAndColumn(@TempDir final Path dir) throws IOException {
        final var file = write(dir, "Broken.java", "class Broken {");
        final var outcome = JavafmtEngine.run(request(dir, false, file));
        assertThat(outcome.changed()).isEmpty();
        assertThat(outcome.errors()).singleElement().satisfies(error -> {
            assertThat(error.source().display()).isEqualTo("Broken.java");
            assertThat(error.line()).isPositive();
            assertThat(error.column()).isPositive();
            assertThat(error.description()).startsWith("Broken.java:" + error.line() + ":" + error.column() + ": error: ");
        });
    }

    @Test
    void reportsAWarningWithItsLineAndColumn(@TempDir final Path dir) throws IOException {
        final var file = write(dir, "Warned.java", WARNED);
        final var outcome = JavafmtEngine.run(request(dir, false, file));
        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.line()).isPositive();
            assertThat(warning.column()).isPositive();
            assertThat(warning.description()).contains("Warned.java:" + warning.line() + ":" + warning.column() + ": warning: ");
        });
    }

    @Test
    void anUnreadableFileIsAnError(@TempDir final Path dir) {
        final var missing = dir.resolve("Missing.java");
        final var outcome = JavafmtEngine.run(request(dir, false, missing));
        assertThat(outcome.errors()).singleElement().satisfies(error -> {
            assertThat(error.line()).isZero();
            assertThat(error.message()).startsWith("cannot read: ");
            assertThat(error.description()).startsWith("Missing.java: error: cannot read: ");
        });
    }

    private static Request request(final Path dir, final boolean write, final Path file) {
        return new Request(List.of(file.toFile()), Optional.empty(), Optional.empty(), write, dir.toFile(), 1);
    }

    private static Path write(final Path dir, final String relative, final String content) throws IOException {
        final var path = dir.resolve(relative);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content);
    }
}
