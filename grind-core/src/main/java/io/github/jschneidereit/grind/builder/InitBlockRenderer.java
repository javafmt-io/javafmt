package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.BlockTree;

import java.util.Optional;
import io.github.jschneidereit.grind.doc.Doc;
import io.github.jschneidereit.grind.doc.LeadingCommentAttacher;

final class InitBlockRenderer {

    static Doc render(final BlockTree block, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var stmts = block.getStatements().stream()
            .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
            .toList();
        final var interior = attacher.interior(block);
        if (block.isStatic()) {
            return BlockRenderer.buildBlock("static", stmts, interior);
        }
        return BlockRenderer.buildBlock(stmts, interior);
    }

    private InitBlockRenderer() {}
}
