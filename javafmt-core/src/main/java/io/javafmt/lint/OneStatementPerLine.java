package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.StatementTree;
import io.javafmt.Position;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.List;

/**
 * Splits multiple statements that share the same source line onto separate lines. Detects
 * pairs by comparing start-line numbers of consecutive statements in a {@link BlockTree}.
 *
 * <p>For loop headers are excluded by design: this rule only visits {@link BlockTree}
 * statement lists, which are block bodies — not the initializer/condition/update clauses of a
 * {@code for} loop header.
 */
public final class OneStatementPerLine implements LintRule {
    @Override
    public LintResult apply(final LintContext context) {
        final var unit = context.unit();
        return LintScan.edits(
                context,
                edits -> new ChainScanner<>() {
                    @Override
                    public Void visitBlock(final BlockTree node, final Void p) {
                        checkBlock(node.getStatements(), unit, edits);
                        return super.visitBlock(node, null);
                    }
                });
    }

    private static void checkBlock(final List<? extends StatementTree> statements, final ParsedUnit unit, final List<LintEdit> edits) {
        for (var i = 0; i < statements.size() - 1; i++) {
            final var current = statements.get(i);
            final var next = statements.get(i + 1);
            if (!(unit.positionOf(current) instanceof Position.At currentAt) || !(unit.positionOf(next) instanceof Position.At nextAt)) {
                continue;
            }
            if (currentAt.line() != nextAt.line()) {
                continue;
            }
            final var currentEnd = unit.endOf(current);
            final var nextStart = unit.startOf(next);
            if (currentEnd < 0 || nextStart < 0 || nextStart <= currentEnd) {
                continue;
            }
            final var src = unit.source();
            var splitPoint = nextStart;
            while (splitPoint > currentEnd && Character.isWhitespace(src.charAt(splitPoint - 1))) {
                splitPoint--;
            }
            final var indent = MultipleVariableDeclarations.extractIndent(src, currentAt.offset());
            edits.add(new LintEdit(splitPoint, nextStart, "\n" + indent));
        }
    }
}
