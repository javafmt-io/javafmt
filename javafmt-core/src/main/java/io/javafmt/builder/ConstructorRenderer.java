package io.javafmt.builder;

import com.sun.source.tree.MethodTree;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;

@UtilityClass
final class ConstructorRenderer {

    Doc render(final MethodTree node, final String className, final Recursor recursor) {
        final var preamble = ModifierRenderer.preambleOf(node.getModifiers(), recursor);
        final var leading = MethodRenderer.buildLeading(preamble.prefix(), node.getTypeParameters(), className, recursor);
        final var signature = MethodRenderer.renderSignature(leading, node.getParameters(), node.getThrows(), recursor);
        final var signatureDoc = MethodRenderer.appendBody(signature, node, recursor);
        return ModifierRenderer.finish(preamble, node.getModifiers(), signatureDoc, recursor);
    }
}
