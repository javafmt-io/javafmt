package io.javafmt.lint;

import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;

/**
 * Replaces the lowercase {@code l} suffix on long literals with uppercase {@code L}.
 * Lowercase {@code l} is visually ambiguous with the digit {@code 1}.
 */
public final class UpperEll implements LintRule {

    @Override
    public String name() {
        return "UpperEll";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var edits = new ArrayList<LintEdit>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitLiteral(final LiteralTree node, final Void p) {
                if (node.getKind() == Tree.Kind.LONG_LITERAL) {
                    final var end = (int) unit.sourcePositions().getEndPosition(unit.tree(), node);
                    if (end > 0 && unit.source().charAt(end - 1) == 'l') {
                        edits.add(new LintEdit(end - 1, end, "L"));
                    }
                }
                return null;
            }
        }.scan(new TreePath(unit.tree()), null);
        return LintResult.ofEdits(edits);
    }
}
