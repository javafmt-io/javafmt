package io.javafmt.builder;

import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
final class FieldRenderer {

    /**
     * A field or local declaration. A field's annotations always take their own lines. On a local,
     * an annotation the author wrote inline stays inline only while it carries no arguments; one
     * that does takes its own line above, as it already would on a method. Inlining every annotation whatever the author wrote left nowhere to break —
     * {@code java/lang/module/Configuration.java} declares three locals as
     * {@code @SuppressWarnings(value = {"rawtypes", "unchecked"}) final Entry<…>[] … = …}, the
     * longest of them 198 characters.
     */
    Doc render(final VariableTree node, final Recursor recursor) {
        final var modifiers = node.getModifiers();
        final var preamble = ModifierRenderer.preambleOf(
                modifiers, recursor, !recursor.isField(node) && ModifierRenderer.rendersLocalAnnotationsInline(modifiers, recursor));
        final var typeDoc = node.getType() == null ? new Doc.Text("var") : recursor.scan(node.getType());
        final var head = Doc.concat(preamble.prefix(), typeDoc, new Doc.Text(" " + Names.of(node)));
        final var declaration = node.getInitializer() == null
                ? Doc.concat(head, new Doc.Text(";"))
                : withInitializer(head, recursor.scan(node.getInitializer()), recursor.tail(node));
        return ModifierRenderer.finish(preamble, declaration, recursor);
    }

    private Doc withInitializer(final Doc head, final Doc initializer, final List<CommentToken> beforeTerminator) {
        final var value = CommentDocs.closeAfter(initializer, beforeTerminator, ";");
        final var beside = Doc.concat(new Doc.Text(" = "), value);
        final var below = Doc.concat(new Doc.Text(" ="), Doc.continuationIndent(Doc.concat(Doc.HARD_LINE, value)));
        return Doc.concat(head, new Doc.Choice(beside, below, Doc.Choice.Fit.FIRST_BREAK));
    }
}
