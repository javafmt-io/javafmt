package io.javafmt.lint;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.javafmt.Diagnostic;

/**
 * Warns when {@code ==} or {@code !=} is used to compare values where at least one operand
 * is a string literal. String identity comparison instead of {@code .equals()} is almost
 * always a bug.
 *
 * <p>Scoped to cases where at least one operand is a string literal — detecting
 * {@code String}-typed non-literal operands requires type resolution, which is unavailable
 * at lint time.
 */
public final class StringLiteralEquality implements LintRule {

    private static final String MESSAGE = "string comparison using == or != (use .equals() instead)";

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.diagnostics(
                ctx,
                diagnostics -> new TreePathScanner<>() {

                    @Override
                    public Void visitBinary(final BinaryTree node, final Void p) {
                        final var kind = node.getKind();
                        if ((kind == Tree.Kind.EQUAL_TO || kind == Tree.Kind.NOT_EQUAL_TO)
                            && (isStringLiteral(node.getLeftOperand()) || isStringLiteral(node.getRightOperand()))) {
                            diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(node)));
                        }
                        return super.visitBinary(node, null);
                    }
                });
    }

    private static boolean isStringLiteral(final ExpressionTree expr) {
        return expr instanceof LiteralTree lit && lit.getKind() == Tree.Kind.STRING_LITERAL;
    }
}
