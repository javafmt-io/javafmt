package io.github.jschneidereit.grind.lint;

import com.sun.source.tree.NewArrayTree;
import com.sun.source.util.TreeScanner;

import io.github.jschneidereit.grind.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adds a trailing comma to every non-empty array initializer, so that subsequent edits
 * adding or reordering elements produce one-line diffs instead of two-line diffs.
 *
 * <p>Strict mode: matches Checkstyle's {@code ArrayTrailingCommaCheck} with
 * {@code alwaysDemandTrailingComma=true}. Empty initializers ({@code {}}) are skipped, as
 * are array creations without an initializer ({@code new int[5]}).
 *
 * <p>Comma detection looks at the raw source between the last initializer's end position
 * and the array's end position; if any {@code ,} appears in that range, the rule treats
 * the array as already having a trailing comma. This is intentionally simple — a comma
 * inside a comment in that region would be a false positive, but such code is bizarre
 * enough that we accept the trade.
 */
public final class ArrayTrailingComma implements LintRule {

    @Override
    public String name() {
        return "ArrayTrailingComma";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var arrays = collectArraysWithInitializers(unit);
        return LintResult.ofEdits(
            arrays.stream()
                .flatMap(arr -> editFor(arr, unit).stream())
                .toList());
    }

    private static List<NewArrayTree> collectArraysWithInitializers(final ParsedUnit unit) {
        final var collector = new ArrayCollector();
        collector.scan(unit.tree(), null);
        return collector.arrays();
    }

    private static Optional<LintEdit> editFor(final NewArrayTree node, final ParsedUnit unit) {
        final var initializers = node.getInitializers();
        if (initializers == null || initializers.isEmpty()) {
            return Optional.empty();
        }
        final var lastElement = initializers.get(initializers.size() - 1);
        final var lastEnd = (int) unit.sourcePositions().getEndPosition(unit.tree(), lastElement);
        final var arrayEnd = (int) unit.sourcePositions().getEndPosition(unit.tree(), node);
        if (lastEnd < 0 || arrayEnd < 0 || lastEnd > arrayEnd) {
            return Optional.empty();
        }
        final var between = unit.source().substring(lastEnd, arrayEnd);
        if (between.indexOf(',') >= 0) {
            return Optional.empty();
        }
        return Optional.of(LintEdit.insert(lastEnd, ","));
    }

    private static final class ArrayCollector extends TreeScanner<Void, Void> {

        private final ArrayList<NewArrayTree> arrays = new ArrayList<>();

        @Override
        public Void visitNewArray(final NewArrayTree node, final Void p) {
            final var initializers = node.getInitializers();
            if (initializers != null && !initializers.isEmpty()) {
                arrays.add(node);
            }
            return super.visitNewArray(node, null);
        }

        List<NewArrayTree> arrays() {
            return List.copyOf(arrays);
        }
    }

}
