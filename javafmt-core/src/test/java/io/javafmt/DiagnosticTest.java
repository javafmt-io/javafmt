package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiagnosticTest {

    @Test
    void parseErrorIsAnError() {
        final var d = new Diagnostic.ParseError("bad", new Position.At(1, 1, 0));
        assertThat(d.isError()).isTrue();
        assertThat(d.message()).isEqualTo("bad");
        assertThat(d.position()).isEqualTo(new Position.At(1, 1, 0));
    }

    @Test
    void warningIsNotAnError() {
        final var d = new Diagnostic.Warning("hmm", new Position.At(2, 3, 4));
        assertThat(d.isError()).isFalse();
    }

    @Test
    void internalErrorIsAnError() {
        final var d = new Diagnostic.InternalError("boom", new Position.At(8, 9, 10));
        assertThat(d.isError()).isTrue();
        assertThat(d.message()).isEqualTo("boom");
        assertThat(d.position()).isEqualTo(new Position.At(8, 9, 10));
    }

    @Test
    void lintErrorIsAnError() {
        final var d = new Diagnostic.LintError("nope", new Position.At(5, 6, 7));
        assertThat(d.isError()).isTrue();
        assertThat(d.message()).isEqualTo("nope");
        assertThat(d.position()).isEqualTo(new Position.At(5, 6, 7));
    }
}
