package io.javafmt.lint;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.Diagnostic;
import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
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
public final class StringLiteralEquality implements LintRule {

    private static final String MESSAGE =
        "string comparison using == or != (use .equals() instead)";

    @Override
    public String name() {
        return "StringLiteralEquality";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var diagnostics = new ArrayList<Diagnostic>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitBinary(final BinaryTree node, final Void p) {
                final var kind = node.getKind();
                if ((kind == Tree.Kind.EQUAL_TO || kind == Tree.Kind.NOT_EQUAL_TO)
                        && (isStringLiteral(node.getLeftOperand())
                            || isStringLiteral(node.getRightOperand()))) {
                    diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(node)));
                }
                return super.visitBinary(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return new LintResult(List.of(), diagnostics);
    }

    private static boolean isStringLiteral(final ExpressionTree expr) {
        return expr instanceof LiteralTree lit && lit.getKind() == Tree.Kind.STRING_LITERAL;
    }
}
