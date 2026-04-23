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
    void formatWithResultOnInvalidSourceReportsParseError() {
        final var garbage = "class {";
        final var result = Grind.formatWithResult(garbage);
        assertThat(result.output()).isEqualTo(garbage);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0).kind()).isEqualTo(Diagnostic.Kind.PARSE_ERROR);
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
}
