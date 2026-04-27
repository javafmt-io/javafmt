package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiagnosticTest {

    @Test
    void parseErrorIsAnError() {
        final Diagnostic d = new Diagnostic.ParseError("bad", new Position(1, 1, 0));
        assertThat(d.isError()).isTrue();
        assertThat(d.message()).isEqualTo("bad");
        assertThat(d.position()).isEqualTo(new Position(1, 1, 0));
    }

    @Test
    void warningIsNotAnError() {
        final Diagnostic d = new Diagnostic.Warning("hmm", new Position(2, 3, 4));
        assertThat(d.isError()).isFalse();
    }

    @Test
    void lintErrorIsAnError() {
        final Diagnostic d = new Diagnostic.LintError("nope", new Position(5, 6, 7));
        assertThat(d.isError()).isTrue();
        assertThat(d.message()).isEqualTo("nope");
        assertThat(d.position()).isEqualTo(new Position(5, 6, 7));
    }
}
