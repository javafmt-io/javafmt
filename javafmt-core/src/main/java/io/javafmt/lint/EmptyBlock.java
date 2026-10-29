package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.TryTree;
import io.javafmt.Diagnostic;
import io.javafmt.parser.ChainPathScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.List;

/**
 * Warns about empty {@code catch} and {@code finally} blocks, empty {@code synchronized} blocks,
 * empty static initializers and empty {@code switch} bodies. A block holding a comment is
 * suppressed — the comment is the author saying the emptiness is deliberate.
 *
 * <p>A method body is not reported, nor a constructor's. An empty method is usually a design: a
 * no-op implementation of an interface, a listener callback with nothing to do. Checkstyle's
 * {@code EmptyBlockCheck} has no token for a method body either; warning on one was javafmt's own
 * addition, and running javafmt over its own source produced six, every one deliberate.
 */
public final class EmptyBlock implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.diagnostics(
                ctx,
                diagnostics -> new ChainPathScanner<>() {

                    @Override
                    public Void visitTry(final TryTree node, final Void p) {
                        final var fin = node.getFinallyBlock();
                        if (fin != null) {
                            checkEmpty(fin, "finally block", unit, diagnostics);
                        }
                        return super.visitTry(node, null);
                    }

                    @Override
                    public Void visitCatch(final CatchTree node, final Void p) {
                        checkEmpty(node.getBlock(), "catch block", unit, diagnostics);
                        return super.visitCatch(node, null);
                    }

                    @Override
                    public Void visitSynchronized(final SynchronizedTree node, final Void p) {
                        checkEmpty(node.getBlock(), "synchronized block", unit, diagnostics);
                        return super.visitSynchronized(node, null);
                    }

                    @Override
                    public Void visitBlock(final BlockTree node, final Void p) {
                        final var path = getCurrentPath();
                        if (path.getParentPath().getLeaf() instanceof ClassTree
                            && node.isStatic()
                            && node.getStatements().isEmpty()
                            && unit.interiorOf(node).isEmpty()) {
                            diagnostics.add(new Diagnostic.Warning("empty static initializer", unit.positionOf(node)));
                        }
                        return super.visitBlock(node, null);
                    }

                    @Override
                    public Void visitSwitch(final SwitchTree node, final Void p) {
                        if (node.getCases().isEmpty()) {
                            diagnostics.add(new Diagnostic.Warning("empty switch body", unit.positionOf(node)));
                        }
                        return super.visitSwitch(node, null);
                    }
                });
    }

    private static void checkEmpty(final BlockTree block, final String what, final ParsedUnit unit, final List<Diagnostic> diagnostics) {
        if (block.getStatements().isEmpty() && unit.interiorOf(block).isEmpty()) {
            diagnostics.add(new Diagnostic.Warning("empty " + what, unit.positionOf(block)));
        }
    }
}
