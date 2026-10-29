package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
final class InitBlockRenderer {

    Doc render(final BlockTree block, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var stmts = block.getStatements()
                .stream()
                .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
                .toList();
        final var trailing = attacher.trailingInside(block);
        if (block.isStatic()) {
            return BlockRenderer.buildBlock("static", stmts, trailing);
        }
        return BlockRenderer.buildBlock(stmts, trailing);
    }
}
