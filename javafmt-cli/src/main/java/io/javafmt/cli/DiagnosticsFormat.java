package io.javafmt.cli;

import io.javafmt.Diagnostic;

enum DiagnosticsFormat {

    JSON,
    TEXT;

    String diagnostic(final String file, final Diagnostic diagnostic) {
        return switch (this) {
            case JSON -> JsonDiagnostics.line(file, diagnostic);
            case TEXT -> diagnostic.describe(file);
        };
    }

    String fileMessage(final String file, final String message) {
        return switch (this) {
            case JSON -> JsonDiagnostics.cliLine(file, message);
            case TEXT -> file + ": " + message;
        };
    }

    String stdinName() {
        return switch (this) {
            case JSON -> "";
            case TEXT -> "<stdin>";
        };
    }
}
