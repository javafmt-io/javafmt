package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    static Stream<Arguments> kindsAndSeverities() {
        final var unknown = new Position.Unknown();
        return Stream.of(
                Arguments.of(new Diagnostic.ParseError("m", unknown), "parse", "error"),
                Arguments.of(new Diagnostic.LintError("m", unknown), "lint", "error"),
                Arguments.of(new Diagnostic.InternalError("m", unknown), "internal", "error"),
                Arguments.of(new Diagnostic.Warning("m", unknown), "warning", "warning"));
    }

    @ParameterizedTest
    @MethodSource("kindsAndSeverities")
    void eachDiagnosticNamesItsKindAndSeverity(final Diagnostic d, final String kind, final String severity) {
        assertThat(d.kind()).isEqualTo(kind);
        assertThat(d.severity()).isEqualTo(severity);
    }

    @Test
    void aKnownPositionExposesItsLineAndColumn() {
        final Position position = new Position.At(3, 7, 42);
        assertThat(position.line()).isEqualTo(3);
        assertThat(position.column()).isEqualTo(7);
    }

    @Test
    void anUnknownPositionIsLineAndColumnZero() {
        final Position position = new Position.Unknown();
        assertThat(position.line()).isZero();
        assertThat(position.column()).isZero();
    }
}
