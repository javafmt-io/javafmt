package io.javafmt.vscode;

import io.javafmt.Diagnostic;
import io.javafmt.Position;

public record DiagnosticDto(String severity, String message, int line, int col) {

    static DiagnosticDto from(final Diagnostic d) {
        final var severity = d.isError() ? "error" : "warning";
        return switch (d.position()) {
            case Position.At(var line, var col, var ignored) -> new DiagnosticDto(severity, d.message(), line, col);
            case Position.Unknown() -> new DiagnosticDto(severity, d.message(), 0, 0);
        };
    }
}
