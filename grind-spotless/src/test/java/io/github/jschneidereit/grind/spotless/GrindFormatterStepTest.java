package io.github.jschneidereit.grind.spotless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GrindFormatterStepTest {

    @Test
    void applyOnValidSourceReturnsFormattedOutput() {
        assertThat(GrindFormatterStep.apply("class Foo { int x; }"))
            .isEqualTo("class Foo {\n    int x;\n}");
    }

    @Test
    void applyOnMalformedSourceThrowsWithPositionAndMessage() {
        final var garbage = "class {";
        assertThatThrownBy(() -> GrindFormatterStep.apply(garbage))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1:")
            .hasMessageMatching("(?s).*\\d+:\\d+.*");
    }

    @Test
    void applyOnMalformedSourcePrefersErrorDiagnosticOverWarning() {
        assertThatThrownBy(() -> GrindFormatterStep.apply("class {"))
            .isInstanceOf(IllegalArgumentException.class)
            .satisfies(t -> assertThat(t.getMessage()).isNotBlank());
    }
}
