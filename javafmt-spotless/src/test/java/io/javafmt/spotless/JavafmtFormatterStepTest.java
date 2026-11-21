package io.javafmt.spotless;

import io.javafmt.Fixtures;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.JavafmtVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class JavafmtFormatterStepTest {

    private static final String TEST_CLASSPATH = System.getProperty("java.class.path");

    private static final String WIDE_SOURCE = "class Foo { int x; String y; void m() { int a = 1; } }";

    private static final String SKIP_WITNESS_RULE = "analyze.skip=RedundantTypeArguments\n";

    private static final String SKIP_VAR_RULE = "analyze.skip=LocalVarUseVar\n";

    @TempDir
    private Path project;

    @Test
    void createReturnsStepNamedJavafmt() {
        assertThat(JavafmtFormatterStep.create(project.toFile()).getName()).isEqualTo("javafmt");
    }

    @Test
    void createFormatsValidJava() throws Exception {
        final var step = JavafmtFormatterStep.create(project.toFile());
        assertThat(step.format(Fixtures.UNFORMATTED_FOO, null)).isEqualTo(Fixtures.FORMATTED_FOO);
    }

    @Test
    void createThrowsOnParseError() {
        final var step = JavafmtFormatterStep.create(project.toFile());
        assertThatThrownBy(() -> step.format("class {", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("javafmt: 1:");
    }

    @Test
    void createFormatsIdenticallyToJavafmt() throws Exception {
        final var step = JavafmtFormatterStep.create(project.toFile());
        assertThat(step.format(WIDE_SOURCE, null)).isEqualTo(Javafmt.format(WIDE_SOURCE));
    }

    @Test
    void formattedOutputIsIdempotent() throws Exception {
        final var step = JavafmtFormatterStep.create(project.toFile());
        final var once = step.format(WIDE_SOURCE, null);
        assertThat(step.format(once, null)).isEqualTo(once);
    }

    @Test
    void lintReportsJavafmtsWarningsSoSpotlessCanShowThem() throws Exception {
        // A star import trips AvoidStarImport, a warning-only rule: the file still formats, the
        // build still passes, and until now Gradle users were never told.
        assertThat(JavafmtFormatterStep.create(project.toFile()).lint(Fixtures.STAR_IMPORT_SOURCE, null))
                .singleElement()
                .satisfies(lint -> {
                    assertThat(lint.getShortCode()).isEqualTo("javafmt");
                    assertThat(lint.getLineStart()).isEqualTo(1);
                    assertThat(lint.getDetail()).contains("star import");
                });
    }

    @Test
    void lintReportsNothingForSourceThatTripsNoWarning() throws Exception {
        assertThat(JavafmtFormatterStep.create(project.toFile()).lint(Fixtures.FORMATTED_FOO, null))
                .isEmpty();
    }

    @Test
    void createWithAnalysisClasspathRunsTheRulesThatNeedResolvedTypes() throws Exception {
        // RedundantTypeArguments only drops a type witness on a generic method call when it can
        // resolve the types, so this is the difference an analysis classpath makes.
        final var withoutAnalysis = JavafmtFormatterStep.create(project.toFile())
                .format(Fixtures.TYPE_WITNESS_SOURCE, null);
        final var withAnalysis = JavafmtFormatterStep.create(JavafmtConfig.defaults(), TEST_CLASSPATH, project.toFile())
                .format(Fixtures.TYPE_WITNESS_SOURCE, null);
        assertThat(withoutAnalysis).contains("Stream.<String>of");
        assertThat(withAnalysis)
                .contains("Stream.of")
                .doesNotContain("Stream.<String>of");
    }

    @Test
    void anEmptyClasspathMeansNoAnalysisInEveryEntryPoint() throws Exception {
        final var file = project.resolve("Foo.java").toFile();
        final var viaCreate = JavafmtFormatterStep.create(JavafmtConfig.defaults(), "", project.toFile())
                .format(Fixtures.STAR_IMPORT_SOURCE, null);
        final var viaApply = JavafmtFormatterStep.apply(Fixtures.STAR_IMPORT_SOURCE, file, "", 25);
        assertThat(viaCreate).contains("java.util.*");
        assertThat(viaApply).contains("java.util.*");
        final var witnessViaCreate = JavafmtFormatterStep.create(JavafmtConfig.defaults(), "", project.toFile())
                .format(Fixtures.TYPE_WITNESS_SOURCE, null);
        assertThat(witnessViaCreate).contains("Stream.<String>of");
        assertThat(JavafmtFormatterStep.apply(Fixtures.TYPE_WITNESS_SOURCE, file, "", 25))
                .contains("Stream.<String>of");
    }

    @Test
    void aNonEmptyClasspathStillAnalyzesInEveryEntryPoint() throws Exception {
        final var file = project.resolve("Foo.java").toFile();
        assertThat(
                        JavafmtFormatterStep.create(JavafmtConfig.defaults(), TEST_CLASSPATH, project.toFile())
                                .format(Fixtures.STAR_IMPORT_SOURCE, null))
                .doesNotContain("java.util.*");
        assertThat(JavafmtFormatterStep.apply(Fixtures.STAR_IMPORT_SOURCE, file, TEST_CLASSPATH, 25))
                .doesNotContain("java.util.*");
    }

    @Test
    void stepsDifferingOnlyByAnalysisClasspathAreNotEqual() {
        // The classpath changes what the formatter emits, so it has to key the up-to-date check
        // exactly as the version and the config do.
        assertThat(JavafmtFormatterStep.create(JavafmtConfig.defaults(), "/one", project.toFile()))
                .isNotEqualTo(JavafmtFormatterStep.create(JavafmtConfig.defaults(), "/two", project.toFile()));
    }

    @ParameterizedTest
    @MethodSource("projectFileChanges")
    void aProjectFileChangeKeysTheStepOnlyWhereItCanApply(final ThrowingConsumer<Path> setup,
                                                          final ThrowingConsumer<Path> change,
                                                          final String stepDirectory,
                                                          final boolean expectEqual)
            throws Throwable {
        Files.createDirectories(project.resolve(".git"));
        setup.accept(project);
        final var directory = project.resolve(stepDirectory).toFile();
        final var before = JavafmtFormatterStep.create(directory);
        assertThat(before).isEqualTo(JavafmtFormatterStep.create(directory));
        change.accept(project);
        final var after = JavafmtFormatterStep.create(directory);
        if (expectEqual) {
            assertThat(after).isEqualTo(before);
        } else {
            assertThat(after).isNotEqualTo(before);
        }
    }

    static Stream<Arguments> projectFileChanges() {
        final ThrowingConsumer<Path> nothing = _ -> {};
        return Stream.of(
                argumentSet(
                        "the project file of the project directory is edited",
                        (ThrowingConsumer<Path>) root -> write(root, ".javafmt", SKIP_WITNESS_RULE),
                        (ThrowingConsumer<Path>) root -> write(root, ".javafmt", SKIP_VAR_RULE),
                        "",
                        false),
                argumentSet(
                        "a project file appears below the project directory",
                        nothing,
                        (ThrowingConsumer<Path>) root -> write(root, "src/main/java/.javafmt", SKIP_WITNESS_RULE),
                        "",
                        false),
                argumentSet(
                        "a repository root appears below the project directory",
                        (ThrowingConsumer<Path>) root -> {
                            write(root, ".javafmt", SKIP_WITNESS_RULE);
                            Files.createDirectories(root.resolve("module"));
                        },
                        (ThrowingConsumer<Path>) root -> write(root, "module/.git", "gitdir: ../.git/modules/module\n"),
                        "",
                        false),
                argumentSet(
                        "a project file appears in a build output directory",
                        nothing,
                        (ThrowingConsumer<Path>) root -> write(root, "build/classes/.javafmt", SKIP_WITNESS_RULE),
                        "",
                        true),
                argumentSet(
                        "the target of a symlinked project file is edited",
                        (ThrowingConsumer<Path>) root -> {
                            write(root, "shared-settings", SKIP_WITNESS_RULE);
                            Files.createDirectories(root.resolve("src"));
                            Files.createSymbolicLink(root.resolve("src/.javafmt"), root.resolve("shared-settings"));
                        },
                        (ThrowingConsumer<Path>) root -> write(root, "shared-settings", SKIP_VAR_RULE),
                        "",
                        false),
                argumentSet(
                        "the project file above the project directory is edited",
                        (ThrowingConsumer<Path>) root -> {
                            write(root, ".javafmt", SKIP_WITNESS_RULE);
                            Files.createDirectories(root.resolve("module"));
                        },
                        (ThrowingConsumer<Path>) root -> write(root, ".javafmt", SKIP_VAR_RULE),
                        "module",
                        false));
    }

    @Test
    void theKeyIsTheSameInTwoCheckouts(@TempDir final Path first, @TempDir final Path second) throws Exception {
        for (final var checkout : List.of(first, second)) {
            Files.createDirectories(checkout.resolve(".git"));
            write(checkout, "module/src/.javafmt", SKIP_WITNESS_RULE);
            write(checkout, "module/.git", "gitdir: ../.git/modules/module\n");
        }
        assertThat(JavafmtFormatterStep.create(first.toFile())).isEqualTo(JavafmtFormatterStep.create(second.toFile()));
    }

    @Test
    void removingANestedProjectFileRekeysTheStep() throws Exception {
        Files.createDirectories(project.resolve(".git"));
        final var sources = Files.createDirectories(project.resolve("src"));
        final var before = JavafmtFormatterStep.create(project.toFile());
        assertThat(before).isEqualTo(JavafmtFormatterStep.create(project.toFile()));
        Files.writeString(sources.resolve(".javafmt"), SKIP_WITNESS_RULE);
        final var added = JavafmtFormatterStep.create(project.toFile());
        assertThat(added).isNotEqualTo(before);
        Files.delete(sources.resolve(".javafmt"));
        assertThat(JavafmtFormatterStep.create(project.toFile()))
                .isEqualTo(before)
                .isNotEqualTo(added);
    }

    @Test
    void theProjectFileForTheFormattedFileSkipsTheRulesItNames() throws Exception {
        Files.createDirectories(project.resolve(".git"));
        Files.writeString(project.resolve(".javafmt"), SKIP_WITNESS_RULE);
        final var step = JavafmtFormatterStep.create(JavafmtConfig.defaults(), TEST_CLASSPATH, project.toFile());
        assertThat(
                        step.format(
                                Fixtures.TYPE_WITNESS_SOURCE,
                                project.resolve("src/Foo.java").toFile()))
                .contains("Stream.<String>of");
    }

    @Test
    void theProjectFileOfAnotherProjectDoesNotApplyToTheFormattedFile(@TempDir final Path elsewhere) throws Exception {
        Files.createDirectories(project.resolve(".git"));
        Files.writeString(project.resolve(".javafmt"), SKIP_WITNESS_RULE);
        final var step = JavafmtFormatterStep.create(JavafmtConfig.defaults(), TEST_CLASSPATH, project.toFile());
        assertThat(
                        step.format(
                                Fixtures.TYPE_WITNESS_SOURCE,
                                elsewhere.resolve("Foo.java").toFile()))
                .doesNotContain("Stream.<String>of");
    }

    @Test
    void anInvalidProjectFileFailsTheFormatAndSaysWhy(@TempDir final Path elsewhere) throws Exception {
        Files.createDirectories(elsewhere.resolve(".git"));
        Files.writeString(elsewhere.resolve(".javafmt"), "analyze.skip=FinalParameters\n");
        final var step = JavafmtFormatterStep.create(project.toFile());
        assertThatThrownBy(() -> step.format(
                        Fixtures.FORMATTED_FOO, elsewhere.resolve("Foo.java").toFile()))
                .hasMessageStartingWith("javafmt: " + elsewhere.resolve(".javafmt") + ": analyze.skip: FinalParameters does not use the classpath");
    }

    @Test
    void createEmbedsFormatterVersionInSerializedStepState() throws Exception {
        assertThat(serialize(JavafmtFormatterStep.create(project.toFile()))).contains(JavafmtVersion.VERSION);
    }

    @Test
    void stepsDifferingOnlyByFormatterVersionAreNotEqual() {
        final var oldStep = JavafmtFormatterStep.createForVersion("1.0.0", JavafmtConfig.defaults(), project.toFile());
        final var newStep = JavafmtFormatterStep.createForVersion("2.0.0", JavafmtConfig.defaults(), project.toFile());
        assertThat(oldStep).isNotEqualTo(newStep);
        assertThat(oldStep.hashCode()).isNotEqualTo(newStep.hashCode());
    }

    @Test
    void stepsWithSameFormatterVersionAndConfigAreEqual() {
        final var first = JavafmtFormatterStep.createForVersion("1.0.0", JavafmtConfig.defaults(), project.toFile());
        final var second = JavafmtFormatterStep.createForVersion("1.0.0", JavafmtConfig.defaults(), project.toFile());
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @ParameterizedTest
    @MethodSource("configsDifferingFromDefaults")
    void stepsDifferingOnlyByConfigAreNotEqual(final JavafmtConfig changed) {
        assertThat(JavafmtFormatterStep.createForVersion("1.0.0", changed, project.toFile()))
                .isNotEqualTo(JavafmtFormatterStep.createForVersion("1.0.0", JavafmtConfig.defaults(), project.toFile()));
    }

    static Stream<JavafmtConfig> configsDifferingFromDefaults() {
        return Stream.of(
                JavafmtConfig.defaults().withVerifyOutput(false),
                JavafmtConfig.defaults().withLint(false),
                JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar")));
    }

    @Test
    void applyKeepsUnusedNamesForAReleaseWithoutUnnamedVariables() {
        final var source = "class Foo { void m() { try { m(); } catch (IllegalStateException unused) { m(); } } }";
        final var file = project.resolve("Foo.java").toFile();
        assertThat(JavafmtFormatterStep.apply(source, file, "", 25)).contains("catch (final IllegalStateException _)");
        assertThat(JavafmtFormatterStep.apply(source, file, "", 21)).contains("catch (final IllegalStateException unused)");
        assertThat(JavafmtFormatterStep.apply(source, file, TEST_CLASSPATH, 21)).contains("catch (final IllegalStateException unused)");
    }

    @Test
    void applyWithAClasspathProducesValidOutput() {
        assertThat(
                        JavafmtFormatterStep.apply(
                                "class Foo { int x; String y; }",
                                project.resolve("Foo.java").toFile(),
                                TEST_CLASSPATH,
                                25))
                .isEqualTo("class Foo {\n\n    int x;\n    String y;\n}\n");
    }

    @Test
    void applyWithAClasspathThatDoesNotExistFormatsAsWithoutOne() {
        assertThat(
                        JavafmtFormatterStep.apply(
                                Fixtures.UNFORMATTED_FOO,
                                project.resolve("Foo.java").toFile(),
                                "/nonexistent/path.jar",
                                25))
                .isEqualTo(Fixtures.FORMATTED_FOO);
    }

    @Test
    void applyWithAClasspathRemovesARedundantTypeWitness() {
        assertThat(
                        JavafmtFormatterStep.apply(
                                Fixtures.TYPE_WITNESS_SOURCE,
                                project.resolve("Foo.java").toFile(),
                                TEST_CLASSPATH,
                                25))
                .doesNotContain("Stream.<String>of");
    }

    @Test
    void applyHonoursTheProjectFileForTheFile() throws Exception {
        Files.createDirectories(project.resolve(".git"));
        Files.writeString(project.resolve(".javafmt"), SKIP_WITNESS_RULE);
        assertThat(
                        JavafmtFormatterStep.apply(
                                Fixtures.TYPE_WITNESS_SOURCE,
                                project.resolve("Foo.java").toFile(),
                                TEST_CLASSPATH,
                                25))
                .contains("Stream.<String>of");
    }

    private static void write(final Path root, final String relative, final String content) throws IOException {
        final var file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
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
}
