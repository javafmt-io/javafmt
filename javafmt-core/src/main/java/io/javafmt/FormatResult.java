package io.javafmt;

import java.util.List;
import java.util.Objects;

/**
 * The result of formatting one source: the formatted text, and any diagnostics raised along the
 * way.
 *
 * <p>On success, {@code output} is the formatted source. On any failure (a parse error, a lint
 * rule that could not rewrite the source, or an internal javafmt error) {@code output} is the
 * original {@code source} returned unchanged — not reformatted, and not guaranteed to be valid
 * Java — with the failure recorded as a {@link Diagnostic} rather than thrown. Callers that want
 * exceptions instead should inspect {@link #hasErrors()} and throw themselves.
 *
 * @param output the formatted source, or the original source unchanged if formatting failed
 * @param diagnostics errors and warnings raised while formatting, in no particular order
 */
public record FormatResult(String output, List<Diagnostic> diagnostics) {

    public FormatResult {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }

    /** Whether any {@link #diagnostics()} entry is an error rather than a warning. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::isError);
    }

    /** The {@link #diagnostics()} entries that are errors. */
    public List<Diagnostic> errors() {
        return diagnostics.stream().filter(Diagnostic::isError).toList();
    }

    /** The {@link #diagnostics()} entries that are warnings, not errors. */
    public List<Diagnostic> warnings() {
        return diagnostics.stream().filter(d -> !d.isError()).toList();
    }
}
