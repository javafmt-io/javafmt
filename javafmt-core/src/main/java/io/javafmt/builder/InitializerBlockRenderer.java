package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;

import java.util.Optional;

@UtilityClass
final class InitializerBlockRenderer {

    Doc render(final BlockTree block, final Recursor recursor) {
        final var statements = block.getStatements()
                .stream()
                .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
                .toList();
        final var trailing = recursor.trailingInside(block);
        if (block.isStatic()) {
            return BlockRenderer.buildBlock("static", statements, trailing);
        }
        return BlockRenderer.buildBlock(statements, trailing);
    }
}
