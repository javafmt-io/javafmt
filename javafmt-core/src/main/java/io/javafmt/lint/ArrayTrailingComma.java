package io.javafmt.lint;

import com.sun.source.tree.NewArrayTree;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.LinkedHashSet;
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
 * <p>Comma detection looks at the source between the last initializer's end position and the
 * array's end position, with comments stripped out first. Reading the raw text was simpler and the
 * comma inside a comment it mistook for the array's own was written off as too strange to meet;
 * {@code java/lang/CharacterDataLatin1.java} annotates its last element with
 * {@code // 255 , hasLower (add 120), hasTitle, …} and javafmt refused the file, because the rule
 * left the source without a comma and the renderer printed one.
 */
public final class ArrayTrailingComma implements ScanningRule {

    @Override
    public RuleScan scan(final LintContext context) {
        final var unit = context.unit();
        return RuleScan.collecting(
                edits -> new ChainScanner<>() {
                    @Override
                    public Void visitNewArray(final NewArrayTree node, final Void p) {
                        editFor(node, unit).ifPresent(edits::add);
                        return super.visitNewArray(node, null);
                    }
                },
                ArrayTrailingComma::distinct);
    }

    /** javac gives a compact constructor's parameters the record components' annotations, so an array in one is visited twice. */
    private static LintResult distinct(final List<LintEdit> edits) {
        return LintResult.ofEdits(List.copyOf(new LinkedHashSet<>(edits)));
    }

    private static Optional<LintEdit> editFor(final NewArrayTree node, final ParsedUnit unit) {
        final var initializers = node.getInitializers();
        if (initializers == null || initializers.isEmpty()) {
            return Optional.empty();
        }
        final var lastElement = initializers.getLast();
        final var lastEnd = unit.endOf(lastElement);
        final var arrayEnd = unit.endOf(node);
        if (lastEnd < 0 || arrayEnd < 0 || lastEnd > arrayEnd) {
            return Optional.empty();
        }
        final var between = CommentScanner.withoutComments(unit.source().substring(lastEnd, arrayEnd));
        if (between.indexOf(',') >= 0) {
            return Optional.empty();
        }
        return Optional.of(TrailingCommas.insertAfter(unit.source(), lastEnd));
    }
}
