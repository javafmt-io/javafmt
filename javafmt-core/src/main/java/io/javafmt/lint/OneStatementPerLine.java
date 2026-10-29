package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.util.TreePathScanner;
import io.javafmt.Position;
import io.javafmt.parser.ParsedUnit;
import java.util.List;

/**
 * Splits multiple statements that share the same source line onto separate lines. Detects
 * pairs by comparing start-line numbers of consecutive statements in a {@link BlockTree}.
 *
 * <p>For loop headers are excluded by design: this rule only visits {@link BlockTree}
 * statement lists, which are block bodies — not the init/condition/update clauses of a
 * {@code for} loop header.
 */
public final class OneStatementPerLine implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
                edits -> new TreePathScanner<>() {

                    @Override
                    public Void visitBlock(final BlockTree node, final Void p) {
                        checkBlock(node.getStatements(), unit, edits);
                        return super.visitBlock(node, null);
                    }
                });
    }

    private static void checkBlock(final List<? extends StatementTree> stmts, final ParsedUnit unit, final List<LintEdit> edits) {
        for (var i = 0; i < stmts.size() - 1; i++) {
            final var curr = stmts.get(i);
            final var next = stmts.get(i + 1);
            if (!(unit.positionOf(curr) instanceof Position.At currAt) || !(unit.positionOf(next) instanceof Position.At nextAt)) {
                continue;
            }
            if (currAt.line() != nextAt.line()) {
                continue;
            }
            final var currEnd = (int) unit.sourcePositions().getEndPosition(unit.tree(), curr);
            final var nextStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), next);
            if (currEnd < 0 || nextStart < 0 || nextStart <= currEnd) {
                continue;
            }
            final var src = unit.source();
            var splitPoint = currEnd;
            while (splitPoint < nextStart && src.charAt(splitPoint) != ';') {
                splitPoint++;
            }
            if (splitPoint < nextStart && src.charAt(splitPoint) == ';') {
                splitPoint++;
            }
            final var indent = MultipleVariableDeclarations.extractIndent(src, currAt.offset());
            edits.add(new LintEdit(splitPoint, nextStart, "\n" + indent));
        }
    }
}
