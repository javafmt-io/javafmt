package io.javafmt.builder;

import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
final class FieldRenderer {
    /**
     * A field or local declaration. An annotation the author wrote inline stays inline only while
     * it carries no arguments; one that does takes its own line above, as it already would on a
     * method. Inlining every annotation whatever the author wrote left nowhere to break —
     * {@code java/lang/module/Configuration.java} declares three locals as
     * {@code @SuppressWarnings(value = {"rawtypes", "unchecked"}) final Entry<…>[] … = …}, the
     * longest of them 198 characters.
     */
    Doc render(final VariableTree node, final Recursor recursor) {
        final var modifiers = node.getModifiers();
        final var preamble = ModifierRenderer.preambleOf(modifiers, recursor, ModifierRenderer.rendersFieldAnnotationsInline(modifiers, recursor));
        final var typeDoc = node.getType() == null ? new Doc.Text("var") : recursor.scan(node.getType());
        final var head = Doc.concat(preamble.prefix(), typeDoc, new Doc.Text(" " + Names.of(node)));
        final var declaration = node.getInitializer() == null
                ? Doc.concat(head, new Doc.Text(";"))
                : withInitializer(head, recursor.scan(node.getInitializer()), recursor.tail(node));
        return ModifierRenderer.finish(preamble, declaration, recursor);
    }

    private Doc withInitializer(final Doc head, final Doc initializer, final List<CommentToken> beforeTerminator) {
        return Doc.concat(head, new Doc.Text(" = "), CommentDocs.closeAfter(initializer, beforeTerminator, ";"));
    }
}
