package io.javafmt.builder;

import com.sun.source.tree.MethodTree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
final class CompactConstructorRenderer {

    Doc render(final MethodTree node, final String className, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var preamble = ModifierRenderer.preambleOf(node.getModifiers(), recursor);
        final var throwsText = node.getThrows().isEmpty()
                ? ""
                : " throws " + node.getThrows().stream().map(Object::toString).collect(Collectors.joining(", "));
        final var header = (Doc) new Doc.Text(preamble.prefix() + className + throwsText);
        final var withBody = MethodRenderer.appendBody(header, node, recursor, attacher);
        return ModifierRenderer.finish(preamble, node.getModifiers(), withBody, recursor);
    }
}
