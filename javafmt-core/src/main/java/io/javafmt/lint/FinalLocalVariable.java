package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import io.javafmt.parser.ChainPathScanner;

/**
 * Adds {@code final} to local variable declarations whose value is never reassigned.
 *
 * <p>Scope: only locals declared as direct children of a {@link BlockTree}. Fields,
 * parameters, enhanced-for variables, catch parameters, and try-with-resources are
 * handled by separate rules (or left alone in v1).
 *
 * <p>Multi-variable declarations are skipped: every declarator in {@code int a, b;} reports the
 * same declaration start offset, so one insertion per declarator would stack up into
 * {@code final final int a, b;}. {@link MultipleVariableDeclarations} splits the group first and
 * this rule sees single declarations on a later iteration.
 *
 * <p>Reassignment detection runs {@link ReassignmentDetector} over the declaration's enclosing
 * block; see that class for which writes count and which nested scopes it skips.
 */
public final class FinalLocalVariable implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
                edits -> new ChainPathScanner<>() {

                    @Override
                    public Void visitVariable(final VariableTree node, final Void p) {
                        final var path = getCurrentPath();
                        if (isLocalDeclaration(path)
                            && !Modifiers.hasFinal(node)
                            && !node.getName().isEmpty()
                            && !MultipleVariableDeclarations.isPartOfDeclarationGroup(path, unit)
                            && !Modifiers.reassigned(path.getParentPath().getLeaf(), node.getName().toString())) {
                            final var pos = (int) unit.sourcePositions().getStartPosition(unit.tree(), node);
                            if (pos >= 0) {
                                edits.add(LintEdit.insert(pos, "final "));
                            }
                        }
                        return super.visitVariable(node, null);
                    }
                });
    }

    private static boolean isLocalDeclaration(final TreePath path) {
        final var parent = path.getParentPath();
        return parent != null && parent.getLeaf() instanceof BlockTree;
    }
}
