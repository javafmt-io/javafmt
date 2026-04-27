package io.github.jschneidereit.grind.lint;

import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.parser.ParsedUnit;

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
 */
public final class LintEngine {

    private static final int MAX_ITERATIONS = 10;

    private final List<LintRule> rules;

    public LintEngine(final List<LintRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    public ParsedUnit lint(final ParsedUnit initial) {
        Objects.requireNonNull(initial, "initial");
        var current = initial;
        for (var i = 0; i < MAX_ITERATIONS; i++) {
            final var unit = current;
            final var edits = rules.stream()
                .flatMap(r -> r.apply(unit).stream())
                .toList();
            if (edits.isEmpty()) {
                return current;
            }
            final var rewritten = applyEdits(current.source(), edits);
            current = JavaParser.parseUnit(rewritten);
        }
        return current;
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
}
