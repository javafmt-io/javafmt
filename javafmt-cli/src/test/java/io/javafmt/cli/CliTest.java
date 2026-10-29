package io.javafmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Fixtures;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CliTest {

    @Test
    void stdinNoArgs_formatsAndWritesToStdout() {
        final var run = runStdin(Fixtures.UNFORMATTED_FOO);
        assertThat(run.exit()).isZero();
        assertThat(run.out()).isEqualTo(Fixtures.FORMATTED_FOO);
    }

    @Test
    void fileArgs_formatsInPlaceAndReturnsZero(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        final var b = dir.resolve("B.java");
        Files.writeString(a, Fixtures.UNFORMATTED_A);
        Files.writeString(b, "class B { int y; }");
        final var run = run(a.toString(), b.toString());
        assertThat(run.exit()).isZero();
        assertThat(Files.readString(a)).isEqualTo(Fixtures.FORMATTED_A);
        assertThat(Files.readString(b)).isEqualTo("class B {\n\n    int y;\n}\n");
    }

    @Test
    void checkFlag_reportsChangesWithoutWriting(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        final var original = Fixtures.UNFORMATTED_A;
        Files.writeString(a, original);
        final var run = run("--check", a.toString());
        assertThat(run.exit()).isEqualTo(1);
        assertThat(Files.readString(a)).isEqualTo(original);
        assertThat(run.err()).contains("would reformat");
    }

    @Test
    void checkFlag_passesForAlreadyFormattedFile(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        Files.writeString(a, Fixtures.FORMATTED_A);
        final var run = run("--check", a.toString());
        assertThat(run.exit()).isZero();
    }

    @Test
    void parseError_onStdinReturnsInputUnchangedAndExitsNonZero() {
        final var run = runStdin("class {");
        assertThat(run.exit()).isEqualTo(1);
        assertThat(run.out()).isEqualTo("class {");
        assertThat(run.err()).contains("error:");
    }

    @Test
    void threads_valid_doesNotFail(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        Files.writeString(a, Fixtures.FORMATTED_A);
        final var run = run("--threads", "4", a.toString());
        assertThat(run.exit()).isZero();
    }

    @Test
    void threads_noValue_failsWithUsefulMessage() {
        final var run = run("--threads");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("--threads");
    }

    @Test
    void threads_nonNumeric_failsWithUsefulMessage() {
        final var run = run("--threads", "xyz");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("--threads");
    }

    @Test
    void threads_negative_failsWithUsefulMessage() {
        final var run = run("--threads", "-1");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("--threads");
    }

    @Test
    void unknownFlag_exitsNonZeroWithMessageNoStackTrace() {
        final var run = run("--bogus");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("javafmt:").contains("--bogus");
        assertThat(run.err()).doesNotContain("Exception").doesNotContain("\tat ");
    }

    @Test
    void batchWithOneMalformedJava_reportsPathAndExitsNonZero(@TempDir final Path dir) throws Exception {
        final var good = dir.resolve("Good.java");
        Files.writeString(good, "class Good {\n\n    int x;\n}\n");
        final var bad = dir.resolve("Bad.java");
        Files.writeString(bad, "class {");
        final var run = run(good.toString(), bad.toString());
        assertThat(run.exit()).isNotZero();
        assertThat(run.err()).contains(bad.toString());
    }

    @Test
    void nonexistentPath_exitsNonZeroWithPathInMessage(@TempDir final Path dir) {
        final var missing = dir.resolve("NoSuchFile.java");
        final var run = run(missing.toString());
        assertThat(run.exit()).isNotZero();
        assertThat(run.err()).contains(missing.toString());
    }

    @Test
    void cacheFlag_skipsUnchangedFilesOnSecondRun(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        Files.writeString(a, Fixtures.UNFORMATTED_A);
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var first = run("--cache", cacheFile.toString(), a.toString());
        assertThat(first.exit()).isZero();
        final var formatted = Files.readString(a);
        assertThat(formatted).isEqualTo(Fixtures.FORMATTED_A);
        assertThat(Files.exists(cacheFile)).isTrue();
        final var mtimeBefore = Files.getLastModifiedTime(a);
        final var second = run("--cache", cacheFile.toString(), a.toString());
        assertThat(second.exit()).isZero();
        assertThat(Files.readString(a)).isEqualTo(formatted);
        assertThat(Files.getLastModifiedTime(a)).isEqualTo(mtimeBefore);
    }

    @Test
    void cacheFlag_checkModeAlsoSkipsUpToDateFiles(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        Files.writeString(a, Fixtures.FORMATTED_A);
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var first = run("--check", "--cache", cacheFile.toString(), a.toString());
        assertThat(first.exit()).isZero();
        assertThat(Files.exists(cacheFile)).isTrue();
        final var second = run("--check", "--cache", cacheFile.toString(), a.toString());
        assertThat(second.exit()).isZero();
    }

    @Test
    void cacheFlag_reformatsFileWhoseContentChanged(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        Files.writeString(a, Fixtures.UNFORMATTED_A);
        final var cacheFile = dir.resolve(".javafmt-cache");
        run("--cache", cacheFile.toString(), a.toString());
        Files.writeString(a, "class A   {   int  y;  }");
        final var second = run("--cache", cacheFile.toString(), a.toString());
        assertThat(second.exit()).isZero();
        assertThat(Files.readString(a)).isEqualTo("class A {\n\n    int y;\n}\n");
    }

    @Test
    void noCacheFlagByDefault_runsFormatterEveryTime(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        Files.writeString(a, Fixtures.FORMATTED_A);
        run(a.toString());
        try (final var list = Files.list(dir)) {
            assertThat(list.map(Path::getFileName).map(Path::toString)).noneMatch(name -> name.contains(".javafmt-cache"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("excludeCases")
    void excludeFlag_dropsMatchingFiles(
            final String label,
            final List<String> filenames,
            final List<String> excludes,
            final List<String> expectedRewritten)
            throws Exception {
        final var dir = Files.createTempDirectory("javafmt-exclude");
        final var unformatted = Fixtures.UNFORMATTED_FOO;
        for (var name : filenames) {
            final var target = dir.resolve(name);
            Files.createDirectories(target.getParent() == null ? dir : target.getParent());
            Files.writeString(target, unformatted);
        }
        final var args = Stream.<String>concat(
                excludes.stream().flatMap(g -> Stream.of("--exclude", g)),
                filenames.stream().map(n -> dir.resolve(n).toString()))
                .toArray(String[]::new);
        final var run = run(args);
        assertThat(run.exit()).isZero();
        for (var name : filenames) {
            final var actual = Files.readString(dir.resolve(name));
            if (expectedRewritten.contains(name)) {
                assertThat(actual)
                        .as("expected rewritten: %s", name)
                        .isEqualTo(Fixtures.FORMATTED_FOO);
            } else {
                assertThat(actual).as("expected skipped: %s", name).isEqualTo(unformatted);
            }
        }
    }

    private static Stream<Arguments> excludeCases() {
        return Stream.of(
                Arguments.of("basenameMatch", List.of("Keep.java", "input.java"), List.of("**/input.java"), List.of("Keep.java")),
                Arguments.of("noMatch", List.of("A.java", "B.java"), List.of("**/nope.java"), List.of("A.java", "B.java")),
                Arguments.of(
                        "multiGlobMatch",
                        List.of("A.java", "input.java", "expected.java"),
                        List.of("**/input.java", "**/expected.java"),
                        List.of("A.java")),
                Arguments.of("fullPathMatch", List.of("nested/skip.java", "Keep.java"), List.of("**/nested/**"), List.of("Keep.java")));
    }

    @Test
    void excludeFlag_noValue_failsWithUsefulMessage() {
        final var run = run("--exclude");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("--exclude");
    }

    @Test
    void analyzeFlag_withoutClasspath_failsWithUsefulMessage() {
        final var run = run("--analyze");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("--analyze").contains("--classpath");
    }

    @Test
    void analyzeFlag_withClasspath_formatsFileSuccessfully(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        Files.writeString(a, Fixtures.UNFORMATTED_A);
        final var run = run("--analyze", "--classpath", "", a.toString());
        assertThat(run.exit()).as("err=%s", run.err()).isZero();
        // No attributed rules yet, so the output matches the parse-only path.
        assertThat(Files.readString(a)).isEqualTo(Fixtures.FORMATTED_A);
    }

    @Test
    void analyzeFlag_withClasspath_producesIdenticalOutputToParseOnly(@TempDir final Path dir) throws Exception {
        final var src = "class Foo { int x = 1; }";
        final var withoutAnalyze = dir.resolve("WithoutAnalyze.java");
        final var withAnalyze = dir.resolve("WithAnalyze.java");
        Files.writeString(withoutAnalyze, src);
        Files.writeString(withAnalyze, src);
        run(withoutAnalyze.toString());
        run("--analyze", "--classpath", "", withAnalyze.toString());
        assertThat(Files.readString(withAnalyze)).isEqualTo(Files.readString(withoutAnalyze));
    }

    @Test
    void analyzeFlag_withClasspathFile_acceptsClasspathFromFile(@TempDir final Path dir) throws Exception {
        final var classpathFile = dir.resolve("classpath.txt");
        Files.writeString(classpathFile, "");
        final var a = dir.resolve("A.java");
        Files.writeString(a, Fixtures.UNFORMATTED_A);
        final var run = run("--analyze", "--classpath-file", classpathFile.toString(), a.toString());
        assertThat(run.exit()).as("err=%s", run.err()).isZero();
    }

    @Test
    void excludeFlag_repeatable_combinesGlobs(@TempDir final Path dir) throws Exception {
        final var keep = dir.resolve("Keep.java");
        final var input = dir.resolve("input.java");
        final var expected = dir.resolve("expected.java");
        final var unformatted = Fixtures.UNFORMATTED_FOO;
        Files.writeString(keep, unformatted);
        Files.writeString(input, unformatted);
        Files.writeString(expected, unformatted);
        final var run = run("--exclude", "**/input.java", "--exclude", "**/expected.java", keep.toString(), input.toString(), expected.toString());
        assertThat(run.exit()).isZero();
        assertThat(Files.readString(keep)).isEqualTo(Fixtures.FORMATTED_FOO);
        assertThat(Files.readString(input)).isEqualTo(unformatted);
        assertThat(Files.readString(expected)).isEqualTo(unformatted);
    }

    @Test
    void checkOnStdin_unformattedInput_exitsNonZero() {
        // `javafmt --check < F.java` returning 0 for unformatted input is a silent CI false-pass:
        // the pipeline reports success while the file needs reformatting.
        final var run = runStdin(Fixtures.UNFORMATTED_FOO, "--check");
        assertThat(run.exit()).isOne();
        assertThat(run.out())
                .as("check mode must not print the reformatted source")
                .isEmpty();
    }

    @Test
    void checkOnStdin_alreadyFormattedInput_exitsZero() {
        final var run = runStdin(Fixtures.FORMATTED_FOO, "--check");
        assertThat(run.exit()).isZero();
    }

    @Test
    void directoryArgument_formatsJavaFilesRecursively(@TempDir final Path dir) throws Exception {
        // `javafmt src/` is the first thing anyone tries; it used to fail with "Is a directory".
        final var nested = Files.createDirectories(dir.resolve("main/java/demo"));
        final var a = Files.writeString(nested.resolve("A.java"), Fixtures.UNFORMATTED_A);
        final var notJava = Files.writeString(dir.resolve("README.md"), "# not java\n");
        final var run = run(dir.toString());
        assertThat(run.exit()).isZero();
        assertThat(Files.readString(a)).isEqualTo(Fixtures.FORMATTED_A);
        assertThat(Files.readString(notJava))
                .as("non-Java files are left alone")
                .isEqualTo("# not java\n");
    }

    @Test
    void helpFlag_printsUsageAndExitsZero() {
        final var run = run("--help");
        assertThat(run.exit()).isZero();
        assertThat(run.out())
                .contains("Usage")
                .contains("--check")
                .contains("--analyze")
                .contains("--exclude");
    }

    @Test
    void versionFlag_printsVersionAndExitsZero() {
        final var run = run("--version");
        assertThat(run.exit()).isZero();
        assertThat(run.out()).contains(io.javafmt.JavafmtVersion.VERSION);
    }

    @Test
    void brokenStdinIsExitTwoWithReadError() {
        final var failing = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("pipe burst");
            }
        };
        final var run = runStdin(failing);
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err())
                .contains("stdin")
                .contains("pipe burst")
                .doesNotContain("file a bug");
    }

    @Test
    void jsonDiagnostics_cleanFileEmitsNothingOnStderr() {
        final var run = runStdin(Fixtures.FORMATTED_FOO, "--diagnostics=json");
        assertThat(run.exit()).isZero();
        assertThat(run.err()).isEmpty();
    }

    @Test
    void jsonDiagnostics_stdoutStaysRawUnescapedSource() {
        final var run = runStdin(Fixtures.UNFORMATTED_FOO, "--diagnostics=json");
        assertThat(run.exit()).isZero();
        assertThat(run.out()).isEqualTo(Fixtures.FORMATTED_FOO);
    }

    @Test
    void jsonDiagnostics_unparseableFileEmitsDiagnosticAndEchoesInputExactly() {
        final var broken = "class {";
        final var run = runStdin(broken, "--diagnostics=json");
        assertThat(run.exit()).isEqualTo(1);
        assertThat(run.out()).isEqualTo(broken);
        assertThat(run.err().strip().lines()).allSatisfy(l -> assertThat(l).startsWith("{\"kind\":\""));
        assertThat(run.err()).contains("\"kind\":\"parse\"");
    }

    @Test
    void textDiagnosticsRemainTheDefault() {
        final var run = runStdin("class {");
        assertThat(run.err()).doesNotContain("{\"kind\"");
    }

    @Test
    void jsonDiagnostics_fileArgumentsEmitNdjsonForWouldReformat(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        Files.writeString(a, Fixtures.UNFORMATTED_A);
        final var run = run("--check", "--diagnostics=json", a.toString());
        assertThat(run.exit()).isEqualTo(1);
        assertThat(run.err()).contains("\"kind\":\"cli\"").contains("would reformat");
        assertThat(run.err().strip().lines()).allSatisfy(l -> assertThat(l).startsWith("{\"kind\":\""));
    }

    @Test
    void jsonDiagnostics_brokenStdinEmitsJsonOnStderr() {
        final var failing = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("pipe burst");
            }
        };
        final var run = runStdin(failing, "--diagnostics=json");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err().strip().lines()).allSatisfy(l -> assertThat(l).startsWith("{\"kind\":\""));
        assertThat(run.err()).contains("pipe burst");
    }

    @Test
    void unknownDiagnosticsFormatIsAUsageError() {
        final var run = runStdin("", "--diagnostics=yaml");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("--diagnostics");
    }

    @Test
    void inPlaceWriteKeepsThePermissionsOfTheFileAndLeavesNoTempFileBehind(@TempDir final Path dir) throws Exception {
        final var a = dir.resolve("A.java");
        Files.writeString(a, Fixtures.UNFORMATTED_A);
        final var permissions = PosixFilePermissions.fromString("rw-r-----");
        Files.setPosixFilePermissions(a, permissions);
        final var run = run(a.toString());
        assertThat(run.exit()).isZero();
        assertThat(Files.readString(a)).isEqualTo(Fixtures.FORMATTED_A);
        assertThat(Files.getPosixFilePermissions(a)).isEqualTo(permissions);
        try (final var entries = Files.list(dir)) {
            assertThat(entries).containsExactly(a);
        }
    }

    @Test
    void aDirectoryArgumentSkipsTheOutputAndDependencyTrees(@TempDir final Path dir) throws Exception {
        final var generated = dir.resolve("build/generated/Gen.java");
        final var source = dir.resolve("src/S.java");
        Files.createDirectories(generated.getParent());
        Files.createDirectories(source.getParent());
        Files.writeString(generated, Fixtures.UNFORMATTED_A);
        Files.writeString(source, Fixtures.UNFORMATTED_A);
        final var run = run(dir.toString());
        assertThat(run.exit()).isZero();
        assertThat(Files.readString(generated)).isEqualTo(Fixtures.UNFORMATTED_A);
        assertThat(Files.readString(source)).isEqualTo(Fixtures.FORMATTED_A);
    }

    @Test
    void noDefaultExcludesFormatsTheOutputTreesToo(@TempDir final Path dir) throws Exception {
        final var generated = dir.resolve("build/generated/Gen.java");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, Fixtures.UNFORMATTED_A);
        final var run = run("--no-default-excludes", dir.toString());
        assertThat(run.exit()).isZero();
        assertThat(Files.readString(generated)).isEqualTo(Fixtures.FORMATTED_A);
    }

    @Test
    void anUnreadableDirectoryFailsTheRunAndItsReadableSiblingsAreStillReported(@TempDir final Path dir) throws Exception {
        final var locked = Files.createDirectory(dir.resolve("locked"));
        Files.writeString(dir.resolve("B.java"), Fixtures.UNFORMATTED_A);
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            final var run = run("--check", dir.toString());
            assertThat(run.exit()).isEqualTo(2);
            assertThat(run.err()).contains("B.java: would reformat").contains("locked");
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    private static final String EQUALS_WITHOUT_HASHCODE = "class Warned {\n\n    @Override\n    public boolean equals(final Object other) {\n        return this == other;\n    }\n}\n";

    @Test
    void aFileArgumentReportsTheWarningsStdinModeReports(@TempDir final Path dir) throws Exception {
        final var file = dir.resolve("Warned.java");
        Files.writeString(file, EQUALS_WITHOUT_HASHCODE);
        final var run = run("--check", file.toString());
        assertThat(run.exit()).isZero();
        assertThat(run.err().strip()).isEqualTo(file + ":1:1: warning: class overrides equals(Object) but not hashCode()");
    }

    @Test
    void aFileArgumentReportsWarningsAsJsonToo(@TempDir final Path dir) throws Exception {
        final var file = dir.resolve("Warned.java");
        Files.writeString(file, EQUALS_WITHOUT_HASHCODE);
        final var run = run("--check", "--diagnostics=json", file.toString());
        assertThat(run.exit()).isZero();
        assertThat(run.err()).contains("\"kind\":\"warning\"").contains("hashCode()");
    }

    @Test
    void aFileArgumentsErrorCarriesLineAndColumn(@TempDir final Path dir) throws Exception {
        final var file = dir.resolve("Broken.java");
        Files.writeString(file, "class Broken {\n    void f() {\n        int x = ;\n    }\n}\n");
        final var run = run("--check", file.toString());
        assertThat(run.exit()).isEqualTo(1);
        assertThat(run.err().strip()).isEqualTo(file + ":3:17: error: illegal start of expression");
    }

    @Test
    void aCacheFromAPlainRunIsNotReusedByAnAnalyzeRun(@TempDir final Path dir) throws Exception {
        final var file = dir.resolve("Warned.java");
        Files.writeString(file, EQUALS_WITHOUT_HASHCODE);
        final var cache = dir.resolve("javafmt.cache");
        assertThat(run("--cache", cache.toString(), file.toString()).exit()).isZero();
        final var analyzed = run("--cache", cache.toString(), "--analyze", "--classpath", "", file.toString());
        assertThat(analyzed.exit()).isZero();
        assertThat(analyzed.err()).contains("warning: class overrides equals(Object) but not hashCode()");
    }

    @Test
    void everythingAfterADoubleDashIsAPath(@TempDir final Path dir) throws Exception {
        final var file = dir.resolve("A.java");
        Files.writeString(file, Fixtures.UNFORMATTED_A);
        final var run = run("--", file.toString());
        assertThat(run.exit()).isZero();
        assertThat(Files.readString(file)).isEqualTo(Fixtures.FORMATTED_A);
    }

    @Test
    void aFlagAfterADoubleDashIsTreatedAsAPath() {
        final var run = run("--", "--check");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("--check: read failed: no such file");
    }

    @Test
    void aBareDashReadsStdin() {
        final var run = runStdin(Fixtures.UNFORMATTED_FOO, "-");
        assertThat(run.exit()).isZero();
        assertThat(run.out()).isEqualTo(Fixtures.FORMATTED_FOO);
    }

    @Test
    void aBareDashCannotBeMixedWithFileArguments(@TempDir final Path dir) {
        final var run = run("-", dir.resolve("A.java").toString());
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("'-' reads stdin and cannot be combined with file arguments");
    }

    @Test
    void anUnknownSingleDashFlagIsAUsageErrorNotAPath() {
        final var run = run("-x");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("unknown flag: -x");
    }

    @Test
    void noArgumentsOnATerminalPrintsTheUsageInsteadOfWaitingForever() {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final var exit = Cli.run(
                new String[0],
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                true);
        assertThat(exit).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("Usage: javafmt");
    }

    @Test
    void aFileThatIsNotUtf8SaysSo(@TempDir final Path dir) throws Exception {
        final var file = dir.resolve("Latin.java");
        Files.write(file, new byte[] {(byte) 0xff, (byte) 0xfe, 'a',});
        final var run = run(file.toString());
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains(file + ": read failed: not valid UTF-8");
    }

    @Test
    void aFileThatIsNotThereSaysSo(@TempDir final Path dir) {
        final var file = dir.resolve("Missing.java");
        final var run = run(file.toString());
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains(file + ": read failed: no such file");
    }

    @Test
    void aFileTheUserMayNotReadSaysSo(@TempDir final Path dir) throws Exception {
        final var file = dir.resolve("Locked.java");
        Files.writeString(file, Fixtures.UNFORMATTED_A);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        try {
            final var run = run(file.toString());
            assertThat(run.exit()).isEqualTo(2);
            assertThat(run.err()).contains(file + ": read failed: permission denied");
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    private static Run run(final String... args) {
        return runStdin("", args);
    }

    private static Run runStdin(final String stdin, final String... args) {
        return runStdin(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)), args);
    }

    private static Run runStdin(final InputStream in, final String... args) {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final var exit = Cli.run(args, in, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Run(int exit, String out, String err) {}
}
