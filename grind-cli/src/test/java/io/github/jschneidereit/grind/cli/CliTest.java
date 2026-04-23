package io.github.jschneidereit.grind.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

    @Test
    void stdinNoArgs_formatsAndWritesToStdout() {
        final var in = streamOf("class Foo { int x; }");
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final var exit = Cli.run(new String[] {}, in, print(out), print(err));
        assertThat(exit).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8))
            .isEqualTo("class Foo {\n    int x;\n}");
    }

    @Test
    void fileArgs_formatsInPlaceAndReturnsZero(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        final var b = dir.resolve("B.java");
        Files.writeString(a, "class A { int x; }");
        Files.writeString(b, "class B { int y; }");
        final var err = new ByteArrayOutputStream();
        final var exit = Cli.run(
            new String[] {a.toString(), b.toString()},
            streamOf(""), print(new ByteArrayOutputStream()), print(err));
        assertThat(exit).isZero();
        assertThat(Files.readString(a)).isEqualTo("class A {\n    int x;\n}");
        assertThat(Files.readString(b)).isEqualTo("class B {\n    int y;\n}");
    }

    @Test
    void checkFlag_reportsChangesWithoutWriting(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        final var original = "class A { int x; }";
        Files.writeString(a, original);
        final var err = new ByteArrayOutputStream();
        final var exit = Cli.run(
            new String[] {"--check", a.toString()},
            streamOf(""), print(new ByteArrayOutputStream()), print(err));
        assertThat(exit).isEqualTo(1);
        assertThat(Files.readString(a)).isEqualTo(original);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("would reformat");
    }

    @Test
    void checkFlag_passesForAlreadyFormattedFile(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        Files.writeString(a, "class A {\n    int x;\n}");
        final var exit = Cli.run(
            new String[] {"--check", a.toString()},
            streamOf(""), print(new ByteArrayOutputStream()), print(new ByteArrayOutputStream()));
        assertThat(exit).isZero();
    }

    @Test
    void parseError_onStdinReturnsInputUnchangedAndExitsNonZero() {
        final var err = new ByteArrayOutputStream();
        final var out = new ByteArrayOutputStream();
        final var exit = Cli.run(new String[] {}, streamOf("class {"), print(out), print(err));
        assertThat(exit).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("class {");
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("PARSE_ERROR");
    }

    private static InputStream streamOf(final String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static PrintStream print(final ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }
}
