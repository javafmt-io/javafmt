package io.javafmt.vscode;

import io.javafmt.FormatResult;

import java.util.List;
import java.util.Objects;

public record FormatResponse(String id, String output, List<DiagnosticDto> diagnostics) {

    public FormatResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }

    static FormatResponse from(final String id, final FormatResult result) {
        final var diagnostics = result.diagnostics().stream()
            .map(DiagnosticDto::from)
            .toList();
        return new FormatResponse(id, result.output(), diagnostics);
    }
}
