package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnosticRenderingTest {

    @Test
    void describeUsesCanonicalFileLineColFormat() {
        final var d = new Diagnostic.ParseError("boom", new Position.At(3, 7, 42));
        assertThat(d.describe("Foo.java")).isEqualTo("Foo.java:3:7: error: boom");
        assertThat(new Diagnostic.Warning("hm", Position.UNKNOWN).describe("Foo.java")).isEqualTo("Foo.java: warning: hm");
        assertThat(d.describeBare()).isEqualTo("3:7: boom");
    }

    @Test
    void formatResultSplitsErrorsAndWarnings() {
        final var result = new FormatResult(
                "",
                List.of(new Diagnostic.ParseError("e", Position.UNKNOWN), new Diagnostic.Warning("w", Position.UNKNOWN)));
        assertThat(result.errors()).hasSize(1);
        assertThat(result.warnings()).hasSize(1);
    }
}
