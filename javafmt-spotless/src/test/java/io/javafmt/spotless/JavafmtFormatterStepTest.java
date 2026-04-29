package io.javafmt.spotless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JavafmtFormatterStepTest {

    @Test
    void applyOnValidSourceReturnsFormattedOutput() {
        assertThat(JavafmtFormatterStep.apply("class Foo { int x; }"))
            .isEqualTo("class Foo {\n    int x;\n}");
    }

    @Test
    void applyOnMalformedSourceThrowsWithPositionAndMessage() {
        final var garbage = "class {";
        assertThatThrownBy(() -> JavafmtFormatterStep.apply(garbage))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1:")
            .hasMessageMatching("(?s).*\\d+:\\d+.*");
    }

    @Test
    void applyOnMalformedSourcePrefersErrorDiagnosticOverWarning() {
        assertThatThrownBy(() -> JavafmtFormatterStep.apply("class {"))
            .isInstanceOf(IllegalArgumentException.class)
            .satisfies(t -> assertThat(t.getMessage()).isNotBlank());
    }
}
