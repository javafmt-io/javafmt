package io.github.jschneidereit.grind;

import java.util.List;
import java.util.Objects;

public record FormatResult(String output, List<Diagnostic> diagnostics) {

    public FormatResult {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.kind() == Diagnostic.Kind.PARSE_ERROR);
    }
}
