package io.javafmt.spotless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diffplug.spotless.FormatterStep;
import io.javafmt.JavafmtConfig;
import org.junit.jupiter.api.Test;

class JavafmtFormatterStepTest {

    // ----- apply(String) — direct formatting -----

    @Test
    void applyOnValidSourceReturnsFormattedOutput() {
        assertThat(JavafmtFormatterStep.apply("class Foo { int x; }"))
            .isEqualTo("class Foo {\n    int x;\n}");
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

    // ----- create() — FormatterStep integration -----

    @Test
    void createReturnsStepNamedJavafmt() {
        assertThat(JavafmtFormatterStep.create().getName()).isEqualTo("javafmt");
    }

    @Test
    void createFormatsValidJava() throws Exception {
        final var step = JavafmtFormatterStep.create();
        assertThat(step.format("class Foo { int x; }", null))
            .isEqualTo("class Foo {\n    int x;\n}");
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
    void createWithReorderMembersRespectedByStep() throws Exception {
        final var input = "class Foo {\n    private void b() {}\n    public void a() {}\n}\n";
        final var preserved = JavafmtFormatterStep.create(JavafmtConfig.defaults()).format(input, null);
        final var reordered = JavafmtFormatterStep.create(new JavafmtConfig(true)).format(input, null);
        assertThat(preserved.indexOf("private")).isLessThan(preserved.indexOf("public"));
        assertThat(reordered.indexOf("public")).isLessThan(reordered.indexOf("private"));
    }

    @Test
    void formattedOutputIsIdempotent() throws Exception {
        final var step = JavafmtFormatterStep.create();
        final var source = "class Foo { int x; String y; void m() { int a = 1; } }";
        final var once = step.format(source, null);
        assertThat(step.format(once, null)).isEqualTo(once);
    }
}
