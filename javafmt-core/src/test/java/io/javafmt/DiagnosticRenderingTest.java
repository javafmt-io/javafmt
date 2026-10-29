package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnosticRenderingTest {

    @Test
    void describeUsesCanonicalFileLineColFormat() {
        final var d = new Diagnostic.ParseError("boom", new Position.At(3, 7, 42));
        assertThat(d.describe("Foo.java")).isEqualTo("Foo.java:3:7: error: boom");
        assertThat(new Diagnostic.Warning("hm", new Position.Unknown()).describe("Foo.java")).isEqualTo("Foo.java: warning: hm");
        assertThat(d.describeBare()).isEqualTo("3:7: boom");
    }

    @Test
    void formatResultSplitsErrorsAndWarnings() {
        final var result = new FormatResult(
                "",
                List.of(new Diagnostic.ParseError("e", new Position.Unknown()), new Diagnostic.Warning("w", new Position.Unknown())));
        assertThat(result.errors()).hasSize(1);
        assertThat(result.warnings()).hasSize(1);
    }
}
