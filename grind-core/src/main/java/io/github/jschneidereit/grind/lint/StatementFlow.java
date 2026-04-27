package io.github.jschneidereit.grind.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.YieldTree;

/**
 * Shared control-flow helper for switch-correctness lint rules. A statement "terminates" if
 * it transfers control out of its enclosing case body — {@code break}, {@code return},
 * {@code throw}, {@code continue}, or {@code yield}, or a block ending in any of those.
 *
 * <p>The block-recursion is one level deep on purpose: that handles the common
 * {@code case L: { ... return; }} shape without doing real flow analysis through
 * {@code if}/{@code try}, which is out of scope for v1.
 */
final class StatementFlow {

    static boolean terminates(final StatementTree stmt) {
        if (stmt instanceof BreakTree
                || stmt instanceof ReturnTree
                || stmt instanceof ThrowTree
                || stmt instanceof ContinueTree
                || stmt instanceof YieldTree) {
            return true;
        }
        if (stmt instanceof BlockTree block) {
            final var inner = block.getStatements();
            if (inner.isEmpty()) {
                return false;
            }
            final var last = inner.get(inner.size() - 1);
            return last instanceof BreakTree
                || last instanceof ReturnTree
                || last instanceof ThrowTree
                || last instanceof ContinueTree
                || last instanceof YieldTree;
        }
        return false;
    }

    private StatementFlow() {}
}
