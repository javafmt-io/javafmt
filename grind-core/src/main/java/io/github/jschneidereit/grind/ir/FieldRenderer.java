package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.VariableTree;

final class FieldRenderer {

    static Doc render(final VariableTree node) {
        final var sb = new StringBuilder();
        ModifierRenderer.renderAnnotations(node.getModifiers(), sb);
        ModifierRenderer.renderModifiers(node.getModifiers(), sb);
        sb.append(node.getType());
        sb.append(" ");
        sb.append(node.getName());
        if (node.getInitializer() != null) {
            sb.append(" = ").append(node.getInitializer());
        }
        sb.append(";");
        return new Doc.Text(sb.toString());
    }

    private FieldRenderer() {}
}
