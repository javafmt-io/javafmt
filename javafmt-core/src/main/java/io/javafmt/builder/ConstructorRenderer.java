package io.javafmt.builder;

import com.sun.source.tree.MethodTree;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;

@UtilityClass
final class ConstructorRenderer {

    Doc render(final MethodTree node, final String className, final Recursor recursor) {
        final var modifiers = node.getModifiers();
        final var typeParameters = node.getTypeParameters();
        final var preamble = ModifierRenderer.preambleOf(modifiers, typeParameters, recursor);
        final var name = new Doc.Text(className);
        final var leading = MethodRenderer.buildLeading(preamble.prefix(), typeParameters, name, recursor);
        final var signature = MethodRenderer.renderSignature(leading, MethodRenderer.parametersOf(node), node.getThrows(), recursor);
        final var signatureDoc = MethodRenderer.appendBody(signature, node, recursor);
        return ModifierRenderer.finish(preamble, signatureDoc, recursor);
    }
}
