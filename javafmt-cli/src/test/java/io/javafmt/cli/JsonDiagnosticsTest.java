package io.javafmt.cli;

import io.javafmt.Diagnostic;
import io.javafmt.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class JsonDiagnosticsTest {

    private static Stream<Arguments> kinds() {
        final var at = new Position.At(3, 9, 42);
        return Stream.of(
                Arguments.of(new Diagnostic.ParseError("boom", at), "parse"),
                Arguments.of(new Diagnostic.LintError("boom", at), "lint"),
                Arguments.of(new Diagnostic.ConfigError("boom", at), "config"),
                Arguments.of(new Diagnostic.InternalError("boom", at), "internal"),
                Arguments.of(new Diagnostic.Warning("boom", at), "warning"));
    }

    @ParameterizedTest
    @MethodSource("kinds")
    void eachDiagnosticVariantGetsItsOwnKind(final Diagnostic d, final String expectedKind) {
        assertThat(JsonDiagnostics.line("A.java", d))
                .isEqualTo("{\"kind\":\"" + expectedKind + "\",\"message\":\"boom\",\"file\":\"A.java\",\"line\":3,\"col\":9}");
    }

    @Test
    void unknownPositionRendersZeroLineAndColumn() {
        assertThat(JsonDiagnostics.line("", new Diagnostic.Warning("m", new Position.Unknown())))
                .isEqualTo("{\"kind\":\"warning\",\"message\":\"m\",\"line\":0,\"col\":0}");
    }

    @Test
    void cliLineUsesTheCliKind() {
        assertThat(JsonDiagnostics.cliLine("A.java", "would reformat"))
                .isEqualTo("{\"kind\":\"cli\",\"message\":\"would reformat\",\"file\":\"A.java\",\"line\":0,\"col\":0}");
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("escapes")
    void messagesAreEscapedSoTheLineStaysParseable(final String raw, final String label, final String escaped) {
        assertThat(JsonDiagnostics.line("", new Diagnostic.Warning(raw, new Position.Unknown())))
                .isEqualTo("{\"kind\":\"warning\",\"message\":\"" + escaped + "\",\"line\":0,\"col\":0}");
    }

    private static Stream<Arguments> escapes() {
        return Stream.of(
                Arguments.of("a\"b", "quote", "a\\\"b"),
                Arguments.of("a\\b", "backslash", "a\\\\b"),
                Arguments.of("a\nb", "newline", "a\\nb"),
                Arguments.of("a\rb", "carriage return", "a\\rb"),
                Arguments.of("a\tb", "tab", "a\\tb"),
                Arguments.of("a\bb", "backspace", "a\\bb"),
                Arguments.of("a\fb", "form feed", "a\\fb"),
                Arguments.of("a\u0001b", "control character", "a\\u0001b"),
                Arguments.of("a\u001fb", "highest control character", "a\\u001fb"),
                Arguments.of("a b", "space is not escaped", "a b"),
                Arguments.of("caf\u00e9", "accented letter passes through", "caf\u00e9"),
                Arguments.of("\ud83d\ude00", "non-BMP code point passes through", "\ud83d\ude00"));
    }

    @Test
    void aFilePathWithBackslashesIsEscaped() {
        assertThat(JsonDiagnostics.cliLine("C:\\src\\A.java", "m"))
                .isEqualTo("{\"kind\":\"cli\",\"message\":\"m\",\"file\":\"C:\\\\src\\\\A.java\",\"line\":0,\"col\":0}");
    }
}
