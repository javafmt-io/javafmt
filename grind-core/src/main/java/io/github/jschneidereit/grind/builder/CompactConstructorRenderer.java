package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.MethodTree;

import java.util.stream.Collectors;
import io.github.jschneidereit.grind.doc.Doc;
import io.github.jschneidereit.grind.doc.LeadingCommentAttacher;

final class CompactConstructorRenderer {

    static Doc render(final MethodTree node, final String className, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var annotations = node.getModifiers().getAnnotations();
        final var inlineAnnotation = annotations.size() == 1 && annotations.get(0).getArguments().isEmpty();

        final var sb = new StringBuilder();
        if (inlineAnnotation) {
            ModifierRenderer.renderAnnotations(node.getModifiers(), sb);
        }
        ModifierRenderer.renderModifiers(node.getModifiers(), sb);
        sb.append(className);
        if (!node.getThrows().isEmpty()) {
            sb.append(" throws ").append(node.getThrows().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
        }

        final var header = (Doc) new Doc.Text(sb.toString());
        final var withBody = MethodRenderer.appendBody(header, node, recursor, attacher);

        return inlineAnnotation ? withBody : ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), withBody);
    }

    private CompactConstructorRenderer() {}
}
