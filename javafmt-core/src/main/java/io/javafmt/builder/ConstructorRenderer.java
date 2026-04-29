package io.javafmt.builder;

import com.sun.source.tree.MethodTree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;

final class ConstructorRenderer {

    static Doc render(final MethodTree node, final String className, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var annotations = node.getModifiers().getAnnotations();
        final var inlineAnnotation = annotations.size() == 1 && annotations.get(0).getArguments().isEmpty();

        final var modifiersText = new StringBuilder();
        if (inlineAnnotation) {
            ModifierRenderer.renderAnnotations(node.getModifiers(), modifiersText);
        }
        ModifierRenderer.renderModifiers(node.getModifiers(), modifiersText);

        final var leading = MethodRenderer.buildLeading(modifiersText.toString(), node.getTypeParameters(), className, recursor);
        final var signature = MethodRenderer.renderSignature(leading, node.getParameters(), node.getThrows(), attacher, recursor);
        final var signatureDoc = MethodRenderer.appendBody(signature, node, recursor, attacher);

        return inlineAnnotation ? signatureDoc : ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), signatureDoc);
    }

    private ConstructorRenderer() {}
}
