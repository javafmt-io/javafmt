package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.WhileLoopTree;

import java.util.stream.Collectors;

final class LoopRenderer {

    private LoopRenderer() {}

    static Doc renderFor(final ForLoopTree node, final Recursor recursor) {
        final var init = node.getInitializer().stream()
            .map(s -> BlockRenderer.stripTrailingSemicolon(s.toString()))
            .collect(Collectors.joining(", "));
        final var cond = node.getCondition() == null ? "" : node.getCondition().toString();
        final var update = node.getUpdate().stream()
            .map(s -> BlockRenderer.stripTrailingSemicolon(s.toString()))
            .collect(Collectors.joining(", "));
        return BlockRenderer.buildBlock(
            "for (" + init + "; " + cond + "; " + update + ")",
            BlockRenderer.blockStmts(node.getStatement(), recursor)
        );
    }

    static Doc renderEnhancedFor(final EnhancedForLoopTree node, final Recursor recursor) {
        final var header = "for (" + node.getVariable().getType() + " " + node.getVariable().getName()
            + " : " + node.getExpression() + ")";
        return BlockRenderer.buildBlock(header, BlockRenderer.blockStmts(node.getStatement(), recursor));
    }

    static Doc renderWhile(final WhileLoopTree node, final Recursor recursor) {
        // javac wraps the condition in JCParens, so toString() already includes the outer ()
        return BlockRenderer.buildBlock("while " + node.getCondition(), BlockRenderer.blockStmts(node.getStatement(), recursor));
    }
}
