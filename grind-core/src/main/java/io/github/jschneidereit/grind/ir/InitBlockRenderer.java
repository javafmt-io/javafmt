package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.BlockTree;

import java.util.Optional;

final class InitBlockRenderer {

    static Doc render(final BlockTree block, final Recursor recursor) {
        final var stmts = block.getStatements().stream()
            .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
            .toList();
        if (block.isStatic()) {
            return BlockRenderer.buildBlock("static", stmts);
        }
        return BlockRenderer.buildBlock(stmts);
    }

    private InitBlockRenderer() {}
}
