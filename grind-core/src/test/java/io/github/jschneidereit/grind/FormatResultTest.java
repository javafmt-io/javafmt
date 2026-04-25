package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FormatResultTest {

    @Test
    void formatOnInvalidSourceReturnsInputUnchanged() {
        final var garbage = "class {";
        assertThat(Grind.format(garbage)).isEqualTo(garbage);
    }

    @Test
    void formatWithResultOnInvalidSourceReportsParseErrorWithPosition() {
        final var garbage = "class {";
        final var result = Grind.formatWithResult(garbage);
        assertThat(result.output()).isEqualTo(garbage);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.diagnostics()).isNotEmpty();
        assertThat(result.diagnostics()).allMatch(Diagnostic::isError);
        final var d = result.diagnostics().get(0);
        assertThat(d).isInstanceOf(Diagnostic.ParseError.class);
        assertThat(d.position().line()).isPositive();
        assertThat(d.position().column()).isPositive();
    }

    @Test
    void formatWithResultOnValidSourceHasNoDiagnostics() {
        final var result = Grind.formatWithResult("class Foo { int x; }");
        assertThat(result.output()).isEqualTo("class Foo {\n    int x;\n}");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void formatWithResultOnEmptyInputIsEmpty() {
        final var result = Grind.formatWithResult("");
        assertThat(result.output()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void warningsDoNotFlipHasErrors() {
        final var result = new FormatResult(
            "ok",
            java.util.List.of(new Diagnostic.Warning("just fyi", new Position(1, 1, 0))));
        assertThat(result.hasErrors()).isFalse();
    }
}
