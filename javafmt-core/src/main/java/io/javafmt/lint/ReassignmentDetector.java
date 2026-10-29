package io.javafmt.lint;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import io.javafmt.parser.ChainScanner;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Reports whether a subtree writes to a variable of a given simple name: plain
 * assignment, compound assignment, or increment/decrement. Shared by {@link FinalLocalVariable}
 * and {@link FinalParameters}, which both need the same answer before adding {@code final}. Each
 * scope is walked once, collecting every name written in it, and the set is kept for the next
 * name asked about in the same scope.
 *
 * <p>Scanning stops at {@link LambdaExpressionTree} bodies and {@link ClassTree} declarations.
 * Those are separate scopes that can declare their own variable of the same name, and a local
 * captured by either is already required by Java to be effectively final — so a write found
 * inside one must target a different variable.
 *
 * <p>Array-element assignment ({@code a[0] = 1}) is not a write to {@code a}: the reference is
 * unchanged and {@code final int[] a} still compiles. It is correctly ignored.
 */
final class ReassignmentDetector {

    private final IdentityHashMap<Tree, Set<String>> writtenByScope = new IdentityHashMap<>();

    boolean reassigned(final Tree scope, final String name) {
        return writtenIn(scope).contains(name);
    }

    Set<String> writtenIn(final Tree scope) {
        return writtenByScope.computeIfAbsent(scope, ReassignmentDetector::collectWrittenNames);
    }

    private static Set<String> collectWrittenNames(final Tree scope) {
        final var names = new HashSet<String>();
        new ChainScanner<Void, Void>() {

            @Override
            public Void visitAssignment(final AssignmentTree node, final Void p) {
                addIfName(node.getVariable());
                return super.visitAssignment(node, null);
            }

            @Override
            public Void visitCompoundAssignment(final CompoundAssignmentTree node, final Void p) {
                addIfName(node.getVariable());
                return super.visitCompoundAssignment(node, null);
            }

            @Override
            public Void visitUnary(final UnaryTree node, final Void p) {
                switch (node.getKind()) {
                    case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> addIfName(node.getExpression());
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

            private void addIfName(final Tree target) {
                if (target instanceof IdentifierTree id) {
                    names.add(id.getName().toString());
                }
            }
        }.scan(scope, null);
        return Set.copyOf(names);
    }
}
