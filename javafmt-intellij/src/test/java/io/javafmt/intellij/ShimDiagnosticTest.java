package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShimDiagnosticTest {

    @Test
    void parsesEveryMember() {
        final var line = "{\"kind\":\"internal\",\"message\":\"dropped token\",\"file\":\"A.java\",\"line\":3,\"col\":9}";
        final var parsed = ShimDiagnostic.parse(line).orElseThrow();
        assertThat(parsed.kind()).isEqualTo("internal");
        assertThat(parsed.message()).isEqualTo("dropped token");
        assertThat(parsed.line()).isEqualTo(3);
        assertThat(parsed.col()).isEqualTo(9);
        assertThat(parsed.isInternal()).isTrue();
    }

    @Test
    void unescapesTheMessage() {
        final var line = "{\"kind\":\"warning\",\"message\":\"a \\\"quoted\\\" \\\\ path\\nnext\",\"line\":0,\"col\":0}";
        assertThat(ShimDiagnostic.parse(line).orElseThrow().message()).isEqualTo("a \"quoted\" \\ path\nnext");
    }

    @Test
    void nonInternalKindsAreNotInternal() {
        final var line = "{\"kind\":\"parse\",\"message\":\"m\",\"line\":1,\"col\":1}";
        assertThat(ShimDiagnostic.parse(line).orElseThrow().isInternal()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not json at all", "{\"kind\":\"warning\"", "{\"message\":\"no kind\"}"})
    void malformedLinesYieldEmptyRatherThanThrowing(final String line) {
        assertThat(ShimDiagnostic.parse(line)).isEmpty();
    }

    @Test
    void missingPositionMembersDefaultToZero() {
        assertThat(ShimDiagnostic.parse("{\"kind\":\"warning\",\"message\":\"m\"}").orElseThrow().line()).isZero();
    }

    @Test
    void nonHexDigitsAfterUnicodeEscapeDefaultToLiteral() {
        final var line = "{\"kind\":\"warning\",\"message\":\"a\\uZZZZb\",\"line\":0,\"col\":0}";
        assertThat(ShimDiagnostic.parse(line)).isNotEmpty();
        assertThat(ShimDiagnostic.parse(line).orElseThrow().message()).isEqualTo("auZZZZb");
    }

    @Test
    void numericValueOutsideIntRangeDefaultsToZero() {
        final var line = "{\"kind\":\"warning\",\"message\":\"m\",\"line\":2147483648}";
        assertThat(ShimDiagnostic.parse(line)).isNotEmpty();
        assertThat(ShimDiagnostic.parse(line).orElseThrow().line()).isZero();
    }

    @Test
    void negativeNumericOverflowDefaultsToZero() {
        final var line = "{\"kind\":\"warning\",\"message\":\"m\",\"col\":-2147483649}";
        assertThat(ShimDiagnostic.parse(line)).isNotEmpty();
        assertThat(ShimDiagnostic.parse(line).orElseThrow().col()).isZero();
    }

    @Test
    void veryLongMessageDoesNotStackOverflow() {
        final var longMsg = "x".repeat(100000);
        final var line = "{\"kind\":\"warning\",\"message\":\"" + longMsg + "\",\"line\":0,\"col\":0}";
        assertThat(ShimDiagnostic.parse(line)).isNotEmpty();
        assertThat(ShimDiagnostic.parse(line).orElseThrow().message()).isEqualTo(longMsg);
    }

    @Test
    void truncatedUnicodeEscapeAtEndOfMessage() {
        final var line = "{\"kind\":\"warning\",\"message\":\"test\\u12\",\"line\":0,\"col\":0}";
        assertThat(ShimDiagnostic.parse(line)).isNotEmpty();
        assertThat(ShimDiagnostic.parse(line).orElseThrow().message()).isEqualTo("testu12");
    }

    @Test
    void trailingBackslashInMessage() {
        final var line = "{\"kind\":\"warning\",\"message\":\"test\\\\\",\"line\":0,\"col\":0}";
        assertThat(ShimDiagnostic.parse(line)).isNotEmpty();
        assertThat(ShimDiagnostic.parse(line).orElseThrow().message()).isEqualTo("test\\");
    }

    @Test
    void emptyMessageIsValid() {
        final var line = "{\"kind\":\"warning\",\"message\":\"\",\"line\":0,\"col\":0}";
        assertThat(ShimDiagnostic.parse(line)).isNotEmpty();
        assertThat(ShimDiagnostic.parse(line).orElseThrow().message()).isEqualTo("");
    }

    @Test
    void messageContainingFakeLineMemberDoesNotShadowTheRealOne() {
        // The bare quotes around "line" inside the message text (as a diagnostic quoting
        // JSON-like source might produce) must not be mistaken for the real `line` member.
        final var line = "{\"kind\":\"warning\",\"message\":\"embedded \"line\":999 in source\",\"line\":3,\"col\":9}";
        assertThat(ShimDiagnostic.parse(line).orElseThrow().line()).isEqualTo(3);
    }

    @Test
    void messageContainingFakeColMemberDoesNotShadowTheRealOne() {
        final var line = "{\"kind\":\"warning\",\"message\":\"embedded \"col\":999 in source\",\"line\":3,\"col\":9}";
        assertThat(ShimDiagnostic.parse(line).orElseThrow().col()).isEqualTo(9);
    }

    @Test
    void messageContainingFakeKindMemberDoesNotShadowTheRealOne() {
        final var line = "{\"message\":\"embedded \"kind\":\"internal\" in source\",\"kind\":\"warning\",\"line\":1,\"col\":1}";
        assertThat(ShimDiagnostic.parse(line).orElseThrow().kind()).isEqualTo("warning");
    }
}
