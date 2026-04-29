package io.javafmt.lint;

import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParsedUnit;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Runs {@link LintRule}s against a {@link ParsedUnit}, applying their edits to the source
 * and iterating until a fixed point. The first iteration reuses the supplied parse; only
 * subsequent iterations re-parse, so clean files pay no extra parse cost.
 *
 * <p>Edits within a single iteration must not overlap; an overlap is treated as a rule bug
 * and surfaces as an {@link IllegalStateException}.
 *
 * <p>Diagnostics from the final iteration (the one whose edits are empty) are returned as
 * the stable, persistent set. Diagnostics from intermediate iterations are discarded
 * because the corresponding source state no longer exists after the next round of edits is
 * applied.
 */
public final class LintEngine {

    private static final int MAX_ITERATIONS = 10;

    private final List<LintRule> rules;

    public LintEngine(final List<LintRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    public Outcome lint(final ParsedUnit initial) {
        Objects.requireNonNull(initial, "initial");
        var current = initial;
        var lastDiagnostics = List.<Diagnostic>of();
        for (var i = 0; i < MAX_ITERATIONS; i++) {
            final var unit = current;
            final var results = rules.stream().map(r -> r.apply(unit)).toList();
            final var edits = results.stream().flatMap(r -> r.edits().stream()).toList();
            lastDiagnostics = results.stream().flatMap(r -> r.diagnostics().stream()).toList();
            if (edits.isEmpty()) {
                return new Outcome(current, lastDiagnostics);
            }
            final var rewritten = applyEdits(current.source(), edits);
            current = JavaParser.parseUnit(rewritten);
        }
        return new Outcome(current, lastDiagnostics);
    }

    static String applyEdits(final String source, final List<LintEdit> edits) {
        final var sorted = edits.stream()
            .sorted(Comparator.comparingInt(LintEdit::start).reversed())
            .toList();
        var lastStart = Integer.MAX_VALUE;
        final var buf = new StringBuilder(source);
        for (final var edit : sorted) {
            if (edit.end() > lastStart) {
                throw new IllegalStateException(
                    "overlapping lint edits: edit ending at " + edit.end()
                        + " overlaps with edit starting at " + lastStart);
            }
            if (edit.end() > source.length()) {
                throw new IllegalStateException(
                    "edit end " + edit.end() + " exceeds source length " + source.length());
            }
            buf.replace(edit.start(), edit.end(), edit.replacement());
            lastStart = edit.start();
        }
        return buf.toString();
    }

    public record Outcome(ParsedUnit unit, List<Diagnostic> diagnostics) {

        public Outcome {
            Objects.requireNonNull(unit, "unit");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }
}
