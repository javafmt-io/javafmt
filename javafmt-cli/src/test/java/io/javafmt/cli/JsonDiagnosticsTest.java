package io.javafmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Diagnostic;
import io.javafmt.Position;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JsonDiagnosticsTest {

    private static Stream<Arguments> kinds() {
        final var at = new Position.At(3, 9, 42);
        return Stream.of(
                Arguments.of(new Diagnostic.ParseError("boom", at), "parse"),
                Arguments.of(new Diagnostic.LintError("boom", at), "lint"),
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
    void internalIsDistinguishableFromEveryOtherKind() {
        final var at = new Position.At(1, 1, 0);
        final var internal = JsonDiagnostics.line("", new Diagnostic.InternalError("m", at));
        final var others = Stream.of(new Diagnostic.ParseError("m", at), new Diagnostic.LintError("m", at), new Diagnostic.Warning("m", at))
                .map(d -> JsonDiagnostics.line("", d));
        assertThat(internal).contains("\"kind\":\"internal\"");
        assertThat(others).noneMatch(l -> l.contains("\"kind\":\"internal\""));
    }

    @Test
    void unknownPositionRendersZeroLineAndColumn() {
        assertThat(JsonDiagnostics.line("", new Diagnostic.Warning("m", new Position.Unknown())))
                .isEqualTo("{\"kind\":\"warning\",\"message\":\"m\",\"line\":0,\"col\":0}");
    }

    @Test
    void blankFileOmitsTheFileMember() {
        assertThat(JsonDiagnostics.line("", new Diagnostic.Warning("m", new Position.Unknown())))
                .doesNotContain("\"file\"");
    }

    @Test
    void cliLineUsesTheCliKind() {
        assertThat(JsonDiagnostics.cliLine("A.java", "would reformat"))
                .isEqualTo("{\"kind\":\"cli\",\"message\":\"would reformat\",\"file\":\"A.java\",\"line\":0,\"col\":0}");
    }

    @Test
    void messagesAreEscapedSoTheLineStaysParseable() {
        final var nasty = "quote \" backslash \\ newline \n tab \t control ";
        final var rendered = JsonDiagnostics.line("", new Diagnostic.Warning(nasty, new Position.Unknown()));
        assertThat(rendered)
                .contains("quote \\\" backslash \\\\ newline \\n tab \\t control \\u0001")
                .doesNotContain("\n");
    }
}
