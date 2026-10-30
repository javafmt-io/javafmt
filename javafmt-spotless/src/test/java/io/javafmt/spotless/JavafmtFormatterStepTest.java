package io.javafmt.spotless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.javafmt.Diagnostic;
import io.javafmt.Fixtures;
import io.javafmt.FormatResult;
import io.javafmt.JavafmtConfig;
import io.javafmt.JavafmtVersion;
import io.javafmt.Position;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JavafmtFormatterStepTest {

    /** Formats cleanly, but trips the warning-only {@code AvoidStarImport} rule. */
    private static final String STAR_IMPORT_SOURCE = "import java.util.*;\n\nclass Foo {\n\n    List<String> xs;\n}\n";

    /** The type witness on {@code of} is redundant, but only a rule with resolved types can tell. */
    private static final String TYPE_WITNESS_SOURCE = "import java.util.stream.Stream;\n\nclass Foo {\n\n    Stream<String> xs = Stream.<String>of(\"a\");\n}\n";

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
        assertThat(JavafmtFormatterStep.create().getName()).isEqualTo("javafmt");
    }

    @Test
    void createFormatsValidJava() throws Exception {
        final var step = JavafmtFormatterStep.create();
        assertThat(step.format(Fixtures.UNFORMATTED_FOO, null))
                .isEqualTo(Fixtures.FORMATTED_FOO);
    }

    @Test
    void createThrowsOnParseError() {
        final var step = JavafmtFormatterStep.create();
        assertThatThrownBy(() -> step.format("class {", null))
                .isInstanceOf(Exception.class)
                .hasMessageMatching("(?s).*\\d+:\\d+.*");
    }

    @Test
    void createFormatsIdenticallyToApply() throws Exception {
        final var source = "class Foo { int x; String y; void m() { int a = 1; } }";
        final var step = JavafmtFormatterStep.create();
        assertThat(step.format(source, null)).isEqualTo(JavafmtFormatterStep.apply(source));
    }

    @Test
    void formattedOutputIsIdempotent() throws Exception {
        final var step = JavafmtFormatterStep.create();
        final var source = "class Foo { int x; String y; void m() { int a = 1; } }";
        final var once = step.format(source, null);
        assertThat(step.format(once, null)).isEqualTo(once);
    }

    @Test
    void lintReportsJavafmtsWarningsSoSpotlessCanShowThem() throws Exception {
        // A star import trips AvoidStarImport, a warning-only rule: the file still formats, the
        // build still passes, and until now Gradle users were never told.
        final var lints = JavafmtFormatterStep.create().lint(STAR_IMPORT_SOURCE, null);
        assertThat(lints).isNotEmpty();
        assertThat(lints.getFirst().getDetail()).isNotBlank();
        assertThat(lints.getFirst().getLineStart()).isPositive();
    }

    @Test
    void lintReportsNothingForSourceThatTripsNoWarning() throws Exception {
        assertThat(JavafmtFormatterStep.create().lint(Fixtures.FORMATTED_FOO, null)).isEmpty();
    }

    @Test
    void createWithAnalysisClasspathRunsTheRulesThatNeedResolvedTypes() throws Exception {
        // RedundantTypeArguments only drops a type witness on a generic method call when it can
        // resolve the types, so this is the difference an analysis classpath makes.
        final var withoutAnalysis = JavafmtFormatterStep.create().format(TYPE_WITNESS_SOURCE, null);
        final var withAnalysis = JavafmtFormatterStep.create(JavafmtConfig.defaults(), System.getProperty("java.class.path"))
                .format(TYPE_WITNESS_SOURCE, null);
        assertThat(withoutAnalysis).contains("Stream.<String>of");
        assertThat(withAnalysis)
                .contains("Stream.of")
                .doesNotContain("Stream.<String>of");
    }

    @Test
    void stepsDifferingOnlyByAnalysisClasspathAreNotEqual() {
        // The classpath changes what the formatter emits, so it has to key the up-to-date check
        // exactly as the version and the config do.
        assertThat(JavafmtFormatterStep.create(JavafmtConfig.defaults(), "/one"))
                .isNotEqualTo(JavafmtFormatterStep.create(JavafmtConfig.defaults(), "/two"));
    }

    @Test
    void createEmbedsFormatterVersionInSerializedStepState() throws Exception {
        assertThat(serialize(JavafmtFormatterStep.create())).contains(JavafmtVersion.VERSION);
    }

    @Test
    void stepsDifferingOnlyByFormatterVersionAreNotEqual() {
        final var oldStep = JavafmtFormatterStep.createForVersion(JavafmtConfig.defaults(), "", "1.0.0");
        final var newStep = JavafmtFormatterStep.createForVersion(JavafmtConfig.defaults(), "", "2.0.0");
        assertThat(oldStep).isNotEqualTo(newStep);
        assertThat(oldStep.hashCode()).isNotEqualTo(newStep.hashCode());
    }

    @Test
    void stepsWithSameFormatterVersionAndConfigAreEqual() {
        final var first = JavafmtFormatterStep.createForVersion(JavafmtConfig.defaults(), "", "1.0.0");
        final var second = JavafmtFormatterStep.createForVersion(JavafmtConfig.defaults(), "", "1.0.0");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @ParameterizedTest
    @MethodSource("configsDifferingFromDefaults")
    void stepsDifferingOnlyByConfigAreNotEqual(final JavafmtConfig changed) {
        assertThat(JavafmtFormatterStep.createForVersion(changed, "", "1.0.0"))
                .isNotEqualTo(JavafmtFormatterStep.createForVersion(JavafmtConfig.defaults(), "", "1.0.0"));
    }

    static Stream<JavafmtConfig> configsDifferingFromDefaults() {
        return Stream.of(
                JavafmtConfig.defaults().withVerifyOutput(false),
                JavafmtConfig.defaults().withLint(false),
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
    void applyWithAnalyze_withRealClasspath_producesValidOutput() {
        final var source = "class Foo { int x; String y; }";
        assertThat(JavafmtFormatterStep.applyWithAnalyze(source, System.getProperty("java.class.path")))
                .isEqualTo("class Foo {\n\n    int x;\n    String y;\n}\n");
    }

    @Test
    void applyWithAnalyze_withBadClasspath_fallsBackGracefully() {
        final var source = Fixtures.UNFORMATTED_FOO;
        assertThat(JavafmtFormatterStep.applyWithAnalyze(source, "/nonexistent/path.jar"))
                .isEqualTo(JavafmtFormatterStep.apply(source));
    }

    @Test
    void applyWithAnalyze_removesRedundantTypeWitness() {
        // Stream.<String>of() with string args — <String> is inferrable; attribution drops it.
        final var source = "import java.util.stream.Stream; class Foo { void m() { Stream.<String>of(\"a\", \"b\"); } }";
        assertThat(JavafmtFormatterStep.applyWithAnalyze(source, System.getProperty("java.class.path")))
                .doesNotContain("<String>");
    }
}
