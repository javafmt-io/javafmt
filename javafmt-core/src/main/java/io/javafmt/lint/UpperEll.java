package io.javafmt.lint;

import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;

/**
 * Replaces the lowercase {@code l} suffix on long literals with uppercase {@code L}.
 * Lowercase {@code l} is visually ambiguous with the digit {@code 1}.
 */
public final class UpperEll implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
                edits -> new TreePathScanner<>() {

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
                });
    }
}
