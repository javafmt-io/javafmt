package io.javafmt.lint;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;

import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;

import javax.lang.model.element.Modifier;

/**
 * Adds {@code final} to local variable declarations whose value is never reassigned.
 *
 * <p>Scope: only locals declared as direct children of a {@link BlockTree}. Fields,
 * parameters, enhanced-for variables, catch parameters, and try-with-resources are
 * handled by separate rules (or left alone in v1).
 *
 * <p>Reassignment detection scans the declaration's enclosing block recursively but does
 * not descend into {@link LambdaExpressionTree} bodies or {@link ClassTree} declarations:
 * those are separate scopes that can shadow the local. Locals captured by a lambda are
 * already required to be effectively final by Java itself, so any assignment found inside
 * a lambda body must target a different variable with the same name — safe to ignore.
 */
public final class FinalLocalVariable implements LintRule {

    @Override
    public String name() {
        return "FinalLocalVariable";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var edits = new ArrayList<LintEdit>();
        final var collector = new TreePathScanner<Void, Void>() {

            @Override
            public Void visitVariable(final VariableTree node, final Void p) {
                final var path = getCurrentPath();
                if (isLocalDeclaration(path)
                        && !hasFinal(node)
                        && !isReassignedInScope(path, node.getName().toString())) {
                    final var pos = (int) unit.sourcePositions().getStartPosition(unit.tree(), node);
                    if (pos >= 0) {
                        edits.add(LintEdit.insert(pos, "final "));
                    }
                }
                return super.visitVariable(node, null);
            }
        };
        collector.scan(new TreePath(unit.tree()), null);
        return LintResult.ofEdits(edits);
    }

    private static boolean isLocalDeclaration(final TreePath path) {
        final var parent = path.getParentPath();
        return parent != null && parent.getLeaf() instanceof BlockTree;
    }

    private static boolean hasFinal(final VariableTree node) {
        return node.getModifiers().getFlags().contains(Modifier.FINAL);
    }

    private static boolean isReassignedInScope(final TreePath declarationPath, final String name) {
        final var enclosingBlock = declarationPath.getParentPath().getLeaf();
        final var detector = new ReassignmentDetector(name);
        detector.scan(enclosingBlock, null);
        return detector.found;
    }

    private static final class ReassignmentDetector extends TreeScanner<Void, Void> {

        private final String name;
        boolean found;

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
                default -> { }
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

        private boolean targetsName(final Tree target) {
            return target instanceof IdentifierTree id && id.getName().contentEquals(name);
        }
    }
}
