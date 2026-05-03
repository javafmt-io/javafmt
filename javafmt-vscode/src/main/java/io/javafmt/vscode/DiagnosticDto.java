package io.javafmt.vscode;

import io.javafmt.Diagnostic;
import io.javafmt.Position;

public record DiagnosticDto(String severity, String message, int line, int col) {

    static DiagnosticDto from(final Diagnostic d) {
        final var severity = switch (d) {
            case Diagnostic.ParseError ignored -> "error";
            case Diagnostic.LintError ignored -> "error";
            case Diagnostic.Warning ignored -> "warning";
        };
        final var line = switch (d.position()) {
            case Position.At at -> at.line();
            case Position.Unknown ignored -> 0;
        };
        final var col = switch (d.position()) {
            case Position.At at -> at.column();
            case Position.Unknown ignored -> 0;
        };
        return new DiagnosticDto(severity, d.message(), line, col);
    }
}
