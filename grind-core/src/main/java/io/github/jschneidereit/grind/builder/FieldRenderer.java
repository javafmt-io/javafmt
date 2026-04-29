package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.VariableTree;

import java.util.List;
import io.github.jschneidereit.grind.doc.Doc;

final class FieldRenderer {

    static Doc render(final VariableTree node, final Recursor recursor) {
        final var prefix = new StringBuilder();
        ModifierRenderer.renderAnnotations(node.getModifiers(), prefix);
        ModifierRenderer.renderModifiers(node.getModifiers(), prefix);
        final var typeDoc = node.getType() == null ? new Doc.Text("var") : recursor.scan(node.getType());
        final var head = new Doc.Concat(List.of(
            new Doc.Text(prefix.toString()),
            typeDoc,
            new Doc.Text(" " + node.getName())
        ));
        if (node.getInitializer() == null) {
            return new Doc.Concat(List.of(head, new Doc.Text(";")));
        }
        return new Doc.Concat(List.of(
            head,
            new Doc.Text(" = "),
            recursor.scan(node.getInitializer()),
            new Doc.Text(";")
        ));
    }

    private FieldRenderer() {}
}
