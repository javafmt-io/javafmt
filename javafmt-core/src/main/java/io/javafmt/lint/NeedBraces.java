package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Wraps single-statement bodies of {@code if}/{@code else}/{@code while}/{@code do-while}/
 * {@code for}/enhanced-{@code for} in {@code { ... }}. Java's grammar permits a single
 * statement in place of a block, but javafmt requires explicit braces — this rule enforces
 * that requirement automatically rather than failing the build.
 *
 * <p>Each offending body produces two pure insertions: an opening-brace string at its
 * start and a closing-brace string at its end. After the edits apply, re-parsing the
 * source produces a {@link BlockTree} for the body, so a second pass over the same input
 * emits zero edits (idempotency).
 *
 * <p>An {@code else if} chain — an {@link IfTree} whose else statement is itself an
 * {@link IfTree} — is preserved as-is at this rule's level: the inner {@code IfTree} is
 * visited recursively and its own bodies are braced on their own merits.
 */
public final class NeedBraces implements LintRule {

    @Override
    public String name() {
        return "NeedBraces";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var edits = new ArrayList<LintEdit>();
        final var scanner = new TreePathScanner<Void, Void>() {

            @Override
            public Void visitIf(final IfTree node, final Void p) {
                addEditsFor(node.getThenStatement(), edits, unit);
                final var elseStmt = node.getElseStatement();
                if (elseStmt != null && !(elseStmt instanceof IfTree)) {
                    addEditsFor(elseStmt, edits, unit);
                }
                return super.visitIf(node, null);
            }

            @Override
            public Void visitWhileLoop(final WhileLoopTree node, final Void p) {
                addEditsFor(node.getStatement(), edits, unit);
                return super.visitWhileLoop(node, null);
            }

            @Override
            public Void visitDoWhileLoop(final DoWhileLoopTree node, final Void p) {
                addEditsFor(node.getStatement(), edits, unit);
                return super.visitDoWhileLoop(node, null);
            }

            @Override
            public Void visitForLoop(final ForLoopTree node, final Void p) {
                addEditsFor(node.getStatement(), edits, unit);
                return super.visitForLoop(node, null);
            }

            @Override
            public Void visitEnhancedForLoop(final EnhancedForLoopTree node, final Void p) {
                addEditsFor(node.getStatement(), edits, unit);
                return super.visitEnhancedForLoop(node, null);
            }
        };
        scanner.scan(new TreePath(unit.tree()), null);
        return LintResult.ofEdits(edits);
    }

    private static void addEditsFor(
            final @Nullable StatementTree body, final List<LintEdit> edits, final ParsedUnit unit) {
        if (body == null || body instanceof BlockTree) {
            return;
        }
        final var start = (int) unit.sourcePositions().getStartPosition(unit.tree(), body);
        final var end = (int) unit.sourcePositions().getEndPosition(unit.tree(), body);
        if (start < 0 || end < 0) {
            return;
        }
        edits.add(LintEdit.insert(start, "{ "));
        edits.add(LintEdit.insert(end, " }"));
    }
}
