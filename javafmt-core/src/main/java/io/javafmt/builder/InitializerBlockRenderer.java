package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;

@UtilityClass
final class InitializerBlockRenderer {

    Doc render(final BlockTree block, final Recursor recursor) {
        final var statements = block.getStatements().stream()
                .map(recursor::scan)
                .toList();
        final var trailing = recursor.trailingInside(block);
        return BlockRenderer.buildBlock(new Doc.Text(block.isStatic() ? "static {" : "{"), recursor.braceLine(block), statements, trailing);
    }
}
