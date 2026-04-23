package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.VariableTree;

import java.util.List;

final class FieldRenderer {

    static Doc render(final VariableTree node, final Recursor recursor) {
        final var sb = new StringBuilder();
        ModifierRenderer.renderAnnotations(node.getModifiers(), sb);
        ModifierRenderer.renderModifiers(node.getModifiers(), sb);
        sb.append(node.getType() == null ? "var" : node.getType());
        sb.append(" ");
        sb.append(node.getName());
        if (node.getInitializer() == null) {
            sb.append(";");
            return new Doc.Text(sb.toString());
        }
        sb.append(" = ");
        final var initScanned = recursor.scan(node.getInitializer());
        final Doc initDoc = initScanned != null ? initScanned : new Doc.Text(node.getInitializer().toString());
        return new Doc.Concat(List.of(
            new Doc.Text(sb.toString()),
            initDoc,
            new Doc.Text(";")
        ));
    }

    private FieldRenderer() {}
}
