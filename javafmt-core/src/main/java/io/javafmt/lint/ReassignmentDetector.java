package io.javafmt.lint;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.util.TreeScanner;

/**
 * Reports whether a scanned subtree writes to a variable of a given simple name: plain
 * assignment, compound assignment, or increment/decrement. Driven through {@link Modifiers#reassigned},
 * shared by {@link FinalLocalVariable} and {@link FinalParameters}, which both need the same
 * answer before adding {@code final}.
 *
 * <p>Scanning stops at {@link LambdaExpressionTree} bodies and {@link ClassTree} declarations.
 * Those are separate scopes that can declare their own variable of the same name, and a local
 * captured by either is already required by Java to be effectively final — so a write found
 * inside one must target a different variable.
 *
 * <p>Array-element assignment ({@code a[0] = 1}) is not a write to {@code a}: the reference is
 * unchanged and {@code final int[] a} still compiles. It is correctly ignored.
 */
final class ReassignmentDetector extends TreeScanner<Void, Void> {

    private final String name;

    private boolean found;

    ReassignmentDetector(final String name) {
        this.name = name;
    }

    @Override
    public Void visitAssignment(final AssignmentTree node, final Void p) {
        if (targetsName(node.getVariable())) {
            found = true;
        }
        return super.visitAssignment(node, null);
    }

    @Override
    public Void visitCompoundAssignment(final CompoundAssignmentTree node, final Void p) {
        if (targetsName(node.getVariable())) {
            found = true;
        }
        return super.visitCompoundAssignment(node, null);
    }

    @Override
    public Void visitUnary(final UnaryTree node, final Void p) {
        switch (node.getKind()) {
            case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> {
                if (targetsName(node.getExpression())) {
                    found = true;
                }
            }
            default -> {}
        }
        return super.visitUnary(node, null);
    }

    @Override
    public Void visitLambdaExpression(final LambdaExpressionTree node, final Void p) {
        return null;
    }

    @Override
    public Void visitClass(final ClassTree node, final Void p) {
        return null;
    }

    /** Whether the scanned subtree wrote to the variable. Meaningful only after {@code scan}. */
    boolean found() {
        return found;
    }

    private boolean targetsName(final Tree target) {
        return target instanceof IdentifierTree id && id.getName().contentEquals(name);
    }
}
