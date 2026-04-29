package io.javafmt.cli;

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
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("error:");
    }

    @Test
    void threads_valid_doesNotFail(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        Files.writeString(a, "class A {\n    int x;\n}");
        final var exit = Cli.run(
            new String[] {"--threads", "4", a.toString()},
            streamOf(""), print(new ByteArrayOutputStream()), print(new ByteArrayOutputStream()));
        assertThat(exit).isZero();
    }

    @Test
    void threads_noValue_failsWithUsefulMessage() {
        final var err = new ByteArrayOutputStream();
        final var exit = Cli.run(
            new String[] {"--threads"},
            streamOf(""), print(new ByteArrayOutputStream()), print(err));
        assertThat(exit).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("--threads");
    }

    @Test
    void threads_nonNumeric_failsWithUsefulMessage() {
        final var err = new ByteArrayOutputStream();
        final var exit = Cli.run(
            new String[] {"--threads", "xyz"},
            streamOf(""), print(new ByteArrayOutputStream()), print(err));
        assertThat(exit).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("--threads");
    }

    @Test
    void threads_negative_failsWithUsefulMessage() {
        final var err = new ByteArrayOutputStream();
        final var exit = Cli.run(
            new String[] {"--threads", "-1"},
            streamOf(""), print(new ByteArrayOutputStream()), print(err));
        assertThat(exit).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("--threads");
    }

    @Test
    void unknownFlag_exitsNonZeroWithMessageNoStackTrace() {
        final var err = new ByteArrayOutputStream();
        final var exit = Cli.run(
            new String[] {"--bogus"},
            streamOf(""), print(new ByteArrayOutputStream()), print(err));
        assertThat(exit).isEqualTo(2);
        final var errStr = err.toString(StandardCharsets.UTF_8);
        assertThat(errStr).contains("javafmt:").contains("--bogus");
        assertThat(errStr).doesNotContain("Exception").doesNotContain("\tat ");
    }

    @Test
    void batchWithOneMalformedJava_reportsPathAndExitsNonZero(@TempDir final Path dir) throws Exception {
        final var good = dir.resolve("Good.java");
        Files.writeString(good, "class Good {\n    int x;\n}");
        final var bad = dir.resolve("Bad.java");
        Files.writeString(bad, "class {");
        final var err = new ByteArrayOutputStream();
        final var exit = Cli.run(
            new String[] {good.toString(), bad.toString()},
            streamOf(""), print(new ByteArrayOutputStream()), print(err));
        assertThat(exit).isNotZero();
        assertThat(err.toString(StandardCharsets.UTF_8)).contains(bad.toString());
    }

    @Test
    void nonexistentPath_exitsNonZeroWithPathInMessage(@TempDir final Path dir) {
        final var missing = dir.resolve("NoSuchFile.java");
        final var err = new ByteArrayOutputStream();
        final var exit = Cli.run(
            new String[] {missing.toString()},
            streamOf(""), print(new ByteArrayOutputStream()), print(err));
        assertThat(exit).isNotZero();
        assertThat(err.toString(StandardCharsets.UTF_8)).contains(missing.toString());
    }

    private static InputStream streamOf(final String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static PrintStream print(final ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }
}
