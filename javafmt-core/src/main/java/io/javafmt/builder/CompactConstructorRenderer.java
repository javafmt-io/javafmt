package io.javafmt.builder;

import com.sun.source.tree.MethodTree;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
final class CompactConstructorRenderer {

    Doc render(final MethodTree node, final String className, final Recursor recursor) {
        final var preamble = ModifierRenderer.preambleOf(node.getModifiers(), List.of(), recursor);
        final var header = Doc.concat(preamble.prefix(), new Doc.Text(className));
        final var withBody = MethodRenderer.appendBody(header, node, recursor);
        return ModifierRenderer.finish(preamble, withBody, recursor);
    }
}
