package io.javafmt.lint;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import io.javafmt.Diagnostic;
import java.util.List;

/**
 * Warns when {@code ==} or {@code !=} is used to compare values where at least one operand
 * is a string literal. String identity comparison instead of {@code .equals()} is almost
 * always a bug.
 *
 * <p>Scoped to cases where at least one operand is a string literal — detecting
 * {@code String}-typed non-literal operands requires type resolution, which is unavailable
 * at lint time.
 */
public final class StringLiteralEquality implements WarningRule {

    private static final String MESSAGE = "string comparison using == or != (use .equals() instead)";

    @Override
    public List<Diagnostic> warnings(final LintContext ctx) {
        return WarningRule.onNode(ctx, BinaryTree.class, StringLiteralEquality::comparesStringLiteral, MESSAGE);
    }

    private static boolean comparesStringLiteral(final BinaryTree node) {
        final var kind = node.getKind();
        return (kind == Tree.Kind.EQUAL_TO || kind == Tree.Kind.NOT_EQUAL_TO)
            && (isStringLiteral(node.getLeftOperand()) || isStringLiteral(node.getRightOperand()));
    }

    private static boolean isStringLiteral(final ExpressionTree expr) {
        return expr instanceof LiteralTree lit && lit.getKind() == Tree.Kind.STRING_LITERAL;
    }
}
