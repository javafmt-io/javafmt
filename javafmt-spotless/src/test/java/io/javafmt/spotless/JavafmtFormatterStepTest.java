package io.javafmt.spotless;

import io.javafmt.Diagnostic;
import io.javafmt.Fixtures;
import io.javafmt.FormatResult;
import io.javafmt.JavafmtConfig;
import io.javafmt.JavafmtVersion;
import io.javafmt.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavafmtFormatterStepTest {
    @TempDir
    private Path project;

    /** Formats cleanly, but trips the warning-only {@code AvoidStarImport} rule. */
    private static final String STAR_IMPORT_SOURCE = "import java.util.*;\n\nclass Foo {\n    List<String> xs;\n}\n";

    /** The type witness on {@code of} is redundant, but only a rule with resolved types can tell. */
    private static final String TYPE_WITNESS_SOURCE =
            "import java.util.stream.Stream;\n\nclass Foo {\n    Stream<String> xs = Stream.<String>of(\"a\");\n}\n";

    @Test
    void applyOnValidSourceReturnsFormattedOutput() {
        assertThat(JavafmtFormatterStep.apply(Fixtures.UNFORMATTED_FOO))
                .isEqualTo(Fixtures.FORMATTED_FOO);
    }

    @Test
    void applyOnMalformedSourceThrowsWithPositionAndMessage() {
        assertThatThrownBy(() -> JavafmtFormatterStep.apply("class {"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageMatching("(?s).*\\d+:\\d+.*");
    }

    @Test
    void applyOnMalformedSourcePrefersErrorDiagnosticOverWarning() {
        assertThatThrownBy(() -> JavafmtFormatterStep.apply("class {"))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(t -> assertThat(t.getMessage()).isNotBlank());
    }

    @Test
    void throwOnErrorWithUnknownPositionRendersMessageWithoutPositionPrefix() {
        final var result = new FormatResult("ignored", List.of(new Diagnostic.ParseError("boom", new Position.Unknown())));
        assertThatThrownBy(() -> JavafmtFormatterStep.throwOnError(result))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("javafmt: boom")
                .satisfies(t -> assertThat(t.getMessage()).doesNotContain("?:?"));
    }

    @Test
    void createReturnsStepNamedJavafmt() {
        assertThat(JavafmtFormatterStep.create(project.toFile()).getName()).isEqualTo("javafmt");
    }

    @Test
    void createFormatsValidJava() throws Exception {
        final var step = JavafmtFormatterStep.create(project.toFile());
        assertThat(step.format(Fixtures.UNFORMATTED_FOO, null))
                .isEqualTo(Fixtures.FORMATTED_FOO);
    }

    @Test
    void createThrowsOnParseError() {
        final var step = JavafmtFormatterStep.create(project.toFile());
        assertThatThrownBy(() -> step.format("class {", null))
                .isInstanceOf(Exception.class)
                .hasMessageMatching("(?s).*\\d+:\\d+.*");
    }

    @Test
    void createFormatsIdenticallyToApply() throws Exception {
        final var source = "class Foo { int x; String y; void m() { int a = 1; } }";
        final var step = JavafmtFormatterStep.create(project.toFile());
        assertThat(step.format(source, null)).isEqualTo(JavafmtFormatterStep.apply(source));
    }

    @Test
    void formattedOutputIsIdempotent() throws Exception {
        final var step = JavafmtFormatterStep.create(project.toFile());
        final var source = "class Foo { int x; String y; void m() { int a = 1; } }";
        final var once = step.format(source, null);
        assertThat(step.format(once, null)).isEqualTo(once);
    }

    @Test
    void lintReportsJavafmtsWarningsSoSpotlessCanShowThem() throws Exception {
        // A star import trips AvoidStarImport, a warning-only rule: the file still formats, the
        // build still passes, and until now Gradle users were never told.
        final var lints = JavafmtFormatterStep.create(project.toFile()).lint(STAR_IMPORT_SOURCE, null);
        assertThat(lints).isNotEmpty();
        assertThat(lints.getFirst().getDetail()).isNotBlank();
        assertThat(lints.getFirst().getLineStart()).isPositive();
    }

    @Test
    void lintReportsNothingForSourceThatTripsNoWarning() throws Exception {
        assertThat(JavafmtFormatterStep.create(project.toFile()).lint(Fixtures.FORMATTED_FOO, null)).isEmpty();
    }

    @Test
    void createWithAnalysisClasspathRunsTheRulesThatNeedResolvedTypes(@TempDir final Path dir) throws Exception {
        // RedundantTypeArguments only drops a type witness on a generic method call when it can
        // resolve the types, so this is the difference an analysis classpath makes.
        final var withoutAnalysis = JavafmtFormatterStep.create(project.toFile()).format(TYPE_WITNESS_SOURCE, null);
        final var withAnalysis = JavafmtFormatterStep.create(JavafmtConfig.defaults(), System.getProperty("java.class.path"), dir.toFile())
                .format(TYPE_WITNESS_SOURCE, null);
        assertThat(withoutAnalysis).contains("Stream.<String>of");
        assertThat(withAnalysis)
                .contains("Stream.of")
                .doesNotContain("Stream.<String>of");
    }

    @Test
    void stepsDifferingOnlyByAnalysisClasspathAreNotEqual(@TempDir final Path dir) {
        // The classpath changes what the formatter emits, so it has to key the up-to-date check
        // exactly as the version and the config do.
        assertThat(JavafmtFormatterStep.create(JavafmtConfig.defaults(), "/one", dir.toFile()))
                .isNotEqualTo(JavafmtFormatterStep.create(JavafmtConfig.defaults(), "/two", dir.toFile()));
    }

    @Test
    void theProjectFileOfTheProjectDirectoryKeysTheStep(@TempDir final Path dir) throws Exception {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=RedundantTypeArguments\n");
        final var before = JavafmtFormatterStep.create(JavafmtConfig.defaults(), "", dir.toFile());
        assertThat(before).isEqualTo(JavafmtFormatterStep.create(JavafmtConfig.defaults(), "", dir.toFile()));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=LocalVarUseVar\n");
        assertThat(JavafmtFormatterStep.create(JavafmtConfig.defaults(), "", dir.toFile())).isNotEqualTo(before);
    }

    @Test
    void aProjectFileBelowTheProjectDirectoryKeysTheStep(@TempDir final Path dir) throws Exception {
        Files.createDirectories(dir.resolve(".git"));
        final var sources = Files.createDirectories(dir.resolve("src/main/java"));
        final var before = JavafmtFormatterStep.create(dir.toFile());
        assertThat(before).isEqualTo(JavafmtFormatterStep.create(dir.toFile()));
        Files.writeString(sources.resolve(".javafmt"), "analyze.skip=RedundantTypeArguments\n");
        final var added = JavafmtFormatterStep.create(dir.toFile());
        assertThat(added).isNotEqualTo(before);
        Files.writeString(sources.resolve(".javafmt"), "analyze.skip=LocalVarUseVar\n");
        assertThat(JavafmtFormatterStep.create(dir.toFile())).isNotEqualTo(added);
    }

    @Test
    void aRepositoryRootBelowTheProjectDirectoryKeysTheStep(@TempDir final Path dir) throws Exception {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=RedundantTypeArguments\n");
        final var module = Files.createDirectories(dir.resolve("module"));
        final var before = JavafmtFormatterStep.create(dir.toFile());
        assertThat(before).isEqualTo(JavafmtFormatterStep.create(dir.toFile()));
        Files.writeString(module.resolve(".git"), "gitdir: ../.git/modules/module\n");
        assertThat(JavafmtFormatterStep.create(dir.toFile())).isNotEqualTo(before);
    }

    @Test
    void aProjectFileInABuildOutputDirectoryDoesNotKeyTheStep(@TempDir final Path dir) throws Exception {
        Files.createDirectories(dir.resolve(".git"));
        final var output = Files.createDirectories(dir.resolve("build/classes"));
        final var before = JavafmtFormatterStep.create(dir.toFile());
        assertThat(before).isEqualTo(JavafmtFormatterStep.create(dir.toFile()));
        Files.writeString(output.resolve(".javafmt"), "analyze.skip=RedundantTypeArguments\n");
        assertThat(JavafmtFormatterStep.create(dir.toFile())).isEqualTo(before);
    }

    @Test
    void theProjectFileForTheFormattedFileSkipsTheRulesItNames(@TempDir final Path dir) throws Exception {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=RedundantTypeArguments\n");
        final var step = JavafmtFormatterStep.create(JavafmtConfig.defaults(), "", dir.toFile());
        assertThat(step.format(TYPE_WITNESS_SOURCE, dir.resolve("src/Foo.java").toFile())).contains("Stream.<String>of");
    }

    @Test
    void anInvalidProjectFileFailsTheFormatAndSaysWhy(@TempDir final Path dir) throws Exception {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=FinalParameters\n");
        final var step = JavafmtFormatterStep.create(project.toFile());
        assertThatThrownBy(() -> step.format(Fixtures.FORMATTED_FOO, dir.resolve("Foo.java").toFile()))
                .hasMessageContaining(".javafmt")
                .hasMessageContaining("FinalParameters");
    }

    @Test
    void createEmbedsFormatterVersionInSerializedStepState() throws Exception {
        assertThat(serialize(JavafmtFormatterStep.create(project.toFile()))).contains(JavafmtVersion.VERSION);
    }

    @Test
    void stepsDifferingOnlyByFormatterVersionAreNotEqual() {
        final var oldStep = JavafmtFormatterStep.createForVersion(JavafmtConfig.defaults(), project.toFile(), "1.0.0");
        final var newStep = JavafmtFormatterStep.createForVersion(JavafmtConfig.defaults(), project.toFile(), "2.0.0");
        assertThat(oldStep).isNotEqualTo(newStep);
        assertThat(oldStep.hashCode()).isNotEqualTo(newStep.hashCode());
    }

    @Test
    void stepsWithSameFormatterVersionAndConfigAreEqual() {
        final var first = JavafmtFormatterStep.createForVersion(JavafmtConfig.defaults(), project.toFile(), "1.0.0");
        final var second = JavafmtFormatterStep.createForVersion(JavafmtConfig.defaults(), project.toFile(), "1.0.0");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @ParameterizedTest
    @MethodSource("configsDifferingFromDefaults")
    void stepsDifferingOnlyByConfigAreNotEqual(final JavafmtConfig changed) {
        assertThat(JavafmtFormatterStep.createForVersion(changed, project.toFile(), "1.0.0"))
                .isNotEqualTo(JavafmtFormatterStep.createForVersion(JavafmtConfig.defaults(), project.toFile(), "1.0.0"));
    }

    static Stream<JavafmtConfig> configsDifferingFromDefaults() {
        return Stream.of(
                JavafmtConfig.defaults().withVerifyOutput(false), JavafmtConfig.defaults().withLint(false),
                JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar")));
    }

    private static String serialize(final Object o) throws Exception {
        final var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        // ISO-8859-1 maps every byte to a char, so the UTF-8 string constants in the stream survive
        // the decode and stay findable without a length-prefix parse.
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }

    @Test
    void applyKeepsUnusedNamesForAReleaseWithoutUnnamedVariables(@TempDir final Path dir) {
        final var source = "class Foo { void m() { try { m(); } catch (IllegalStateException unused) { m(); } } }";
        final var file = dir.resolve("Foo.java").toFile();
        assertThat(JavafmtFormatterStep.apply(source, file, "", 25)).contains("catch (final IllegalStateException _)");
        assertThat(JavafmtFormatterStep.apply(source, file, "", 21)).contains("catch (final IllegalStateException unused)");
        assertThat(JavafmtFormatterStep.apply(source, file, System.getProperty("java.class.path"), 21))
                .contains("catch (final IllegalStateException unused)");
    }

    @Test
    void applyWithAClasspathProducesValidOutput(@TempDir final Path dir) {
        final var source = "class Foo { int x; String y; }";
        assertThat(JavafmtFormatterStep.apply(source, dir.resolve("Foo.java").toFile(), System.getProperty("java.class.path"), 25))
                .isEqualTo("class Foo {\n    int x;\n    String y;\n}\n");
    }

    @Test
    void applyWithAClasspathThatDoesNotExistFormatsAsWithoutOne(@TempDir final Path dir) {
        final var source = Fixtures.UNFORMATTED_FOO;
        assertThat(JavafmtFormatterStep.apply(source, dir.resolve("Foo.java").toFile(), "/nonexistent/path.jar", 25))
                .isEqualTo(JavafmtFormatterStep.apply(source));
    }

    @Test
    void applyWithAClasspathRemovesARedundantTypeWitness(@TempDir final Path dir) {
        // Stream.<String>of() with string arguments — <String> is inferrable; attribution drops it.
        final var source = "import java.util.stream.Stream; class Foo { void m() { Stream.<String>of(\"a\", \"b\"); } }";
        assertThat(JavafmtFormatterStep.apply(source, dir.resolve("Foo.java").toFile(), System.getProperty("java.class.path"), 25))
                .doesNotContain("<String>");
    }

    @Test
    void applyHonoursTheProjectFileForTheFile(@TempDir final Path dir) throws Exception {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=RedundantTypeArguments\n");
        final var source = "import java.util.stream.Stream; class Foo { void m() { Stream.<String>of(\"a\", \"b\"); } }";
        assertThat(JavafmtFormatterStep.apply(source, dir.resolve("Foo.java").toFile(), System.getProperty("java.class.path"), 25))
                .contains("<String>");
    }
}
