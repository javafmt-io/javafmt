package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.Diagnostic;
import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Warns about empty block bodies in {@code catch}, {@code finally}, method bodies,
 * {@code synchronized} blocks, and static initializers. Blocks with a comment inside
 * are suppressed (treat the comment as intentional documentation).
 *
 * <p>Empty constructors are excluded — they are a common legitimate pattern.
 */
public final class EmptyBlock implements LintRule {

    @Override
    public String name() {
        return "EmptyBlock";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var diagnostics = new ArrayList<Diagnostic>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitTry(final TryTree node, final Void p) {
                final var fin = node.getFinallyBlock();
                if (fin != null && fin.getStatements().isEmpty()
                        && unit.interiorOf(fin).isEmpty()) {
                    diagnostics.add(new Diagnostic.Warning(
                        "empty finally block", unit.positionOf(fin)));
                }
                return super.visitTry(node, null);
            }

            @Override
            public Void visitCatch(final CatchTree node, final Void p) {
                final var block = node.getBlock();
                if (block.getStatements().isEmpty() && unit.interiorOf(block).isEmpty()) {
                    diagnostics.add(new Diagnostic.Warning(
                        "empty catch block", unit.positionOf(block)));
                }
                return super.visitCatch(node, null);
            }

            @Override
            public Void visitSynchronized(final SynchronizedTree node, final Void p) {
                final var block = node.getBlock();
                if (block.getStatements().isEmpty() && unit.interiorOf(block).isEmpty()) {
                    diagnostics.add(new Diagnostic.Warning(
                        "empty synchronized block", unit.positionOf(block)));
                }
                return super.visitSynchronized(node, null);
            }

            @Override
            public Void visitBlock(final BlockTree node, final Void p) {
                final var path = getCurrentPath();
                if (path.getParentPath().getLeaf() instanceof ClassTree && node.isStatic()
                        && node.getStatements().isEmpty() && unit.interiorOf(node).isEmpty()) {
                    diagnostics.add(new Diagnostic.Warning(
                        "empty static initializer", unit.positionOf(node)));
                }
                return super.visitBlock(node, null);
            }

            @Override
            public Void visitMethod(final MethodTree node, final Void p) {
                final var body = node.getBody();
                // Skip constructors (returnType == null) and abstract/native methods (body == null)
                if (body != null && node.getReturnType() != null
                        && body.getStatements().isEmpty() && unit.interiorOf(body).isEmpty()) {
                    diagnostics.add(new Diagnostic.Warning(
                        "empty method body", unit.positionOf(body)));
                }
                return super.visitMethod(node, null);
            }

            @Override
            public Void visitSwitch(final SwitchTree node, final Void p) {
                if (node.getCases().isEmpty()) {
                    diagnostics.add(new Diagnostic.Warning(
                        "empty switch body", unit.positionOf(node)));
                }
                return super.visitSwitch(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return new LintResult(List.of(), diagnostics);
    }
}
