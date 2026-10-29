package io.javafmt.builder;

import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import java.util.List;
import lombok.experimental.UtilityClass;

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
        final var mods = node.getModifiers();
        final var preamble = ModifierRenderer.preambleOf(mods, recursor, ModifierRenderer.rendersFieldAnnotationsInline(mods, recursor));
        final var typeDoc = node.getType() == null ? new Doc.Text("var") : recursor.scan(node.getType());
        final var head = new Doc.Concat(List.of(new Doc.Text(preamble.prefix()), typeDoc, new Doc.Text(" " + Names.of(node))));
        final var declaration = node.getInitializer() == null
                ? new Doc.Concat(List.of(head, new Doc.Text(";")))
                : withInitializer(head, recursor.scan(node.getInitializer()));
        return ModifierRenderer.finish(preamble, node.getModifiers(), declaration, recursor);
    }

    private Doc withInitializer(final Doc head, final Doc initializer) {
        return new Doc.Concat(List.of(head, new Doc.Text(" = "), initializer, terminator(initializer)));
    }

    /**
     * The {@code ;} that ends a declaration, on a line of its own when the initializer finishes with
     * a {@code //} comment. Written straight after such a comment the semicolon is inside it, and
     * the declaration no longer compiles.
     */
    private Doc terminator(final Doc initializer) {
        return Doc.endsWithLineComment(initializer) ? new Doc.Concat(List.of(Doc.HARD_LINE, new Doc.Text(";"))) : new Doc.Text(";");
    }
}
