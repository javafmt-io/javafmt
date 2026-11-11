package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.StatementTree;
import io.javafmt.Position;
import io.javafmt.doc.Pairs;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.List;
import java.util.Optional;

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
        return LintScan.edits(context, edits -> new ChainScanner<>() {
            @Override
            public Void visitBlock(final BlockTree node, final Void p) {
                edits.addAll(splitsWithin(node.getStatements(), unit));
                return super.visitBlock(node, null);
            }
        });
    }

    private static List<LintEdit> splitsWithin(final List<? extends StatementTree> statements, final ParsedUnit unit) {
        return statements.stream()
                .gather(Pairs.<StatementTree, Optional<LintEdit>>pairwise((current, next) -> splitBetween(current, next, unit)))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<LintEdit> splitBetween(final StatementTree current, final StatementTree next, final ParsedUnit unit) {
        if (!(unit.positionOf(current) instanceof Position.At currentAt) || !(unit.positionOf(next) instanceof Position.At nextAt)) {
            return Optional.empty();
        }
        if (currentAt.line() != nextAt.line()) {
            return Optional.empty();
        }
        final var currentEnd = unit.endOf(current);
        final var nextStart = unit.startOf(next);
        if (currentEnd < 0 || nextStart < 0 || nextStart <= currentEnd) {
            return Optional.empty();
        }
        final var src = unit.source();
        var splitPoint = nextStart;
        while (splitPoint > currentEnd && Character.isWhitespace(src.charAt(splitPoint - 1))) {
            splitPoint--;
        }
        final var indent = MultipleVariableDeclarations.extractIndent(src, currentAt.offset());
        return Optional.of(new LintEdit(splitPoint, nextStart, "\n" + indent));
    }
}
