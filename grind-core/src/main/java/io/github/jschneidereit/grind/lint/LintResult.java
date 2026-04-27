package io.github.jschneidereit.grind.lint;

import io.github.jschneidereit.grind.Diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * What a {@link LintRule} produces for a single pass over a {@link
 * io.github.jschneidereit.grind.parser.ParsedUnit}: zero or more {@link LintEdit edits} to
 * apply, plus zero or more {@link Diagnostic diagnostics} for cases the rule wanted to
 * touch but couldn't safely fix.
 *
 * <p>This split mirrors ruff's safe/unsafe distinction: the auto-fix is the safe edit; the
 * diagnostic surfaces the rule's opinion when applying the edit would change behavior or
 * break compilation.
 */
public record LintResult(List<LintEdit> edits, List<Diagnostic> diagnostics) {

    public static final LintResult EMPTY = new LintResult(List.of(), List.of());

    public LintResult {
        edits = List.copyOf(Objects.requireNonNull(edits, "edits"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public static LintResult ofEdits(final List<LintEdit> edits) {
        return new LintResult(edits, List.of());
    }
}
