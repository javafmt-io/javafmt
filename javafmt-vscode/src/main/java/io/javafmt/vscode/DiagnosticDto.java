package io.javafmt.vscode;

import io.javafmt.Diagnostic;

public record DiagnosticDto(String severity, String message, int line, int col) {

    static DiagnosticDto from(final Diagnostic d) {
        return new DiagnosticDto(d.severity(), d.message(), d.position().line(), d.position().column());
    }
}
