package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.YieldTree;
import lombok.experimental.UtilityClass;

/**
 * Shared control-flow helper for switch-correctness lint rules. A statement "terminates" if
 * it transfers control out of its enclosing case body — {@code break}, {@code return},
 * {@code throw}, {@code continue}, or {@code yield}, or a block ending in any of those.
 *
 * <p>The block-recursion is one level deep on purpose: that handles the common
 * {@code case L: { ... return; }} shape without doing real flow analysis through
 * {@code if}/{@code try}, which is out of scope for v1.
 */
@UtilityClass
final class StatementFlow {

    boolean terminates(final StatementTree stmt) {
        return switch (stmt) {
            case BlockTree block -> !block.getStatements().isEmpty() && transfersControl(block.getStatements().getLast());
            default -> transfersControl(stmt);
        };
    }

    private boolean transfersControl(final StatementTree stmt) {
        return switch (stmt) {
            case BreakTree _, ReturnTree _, ThrowTree _, ContinueTree _, YieldTree _ -> true;
            default -> false;
        };
    }
}
