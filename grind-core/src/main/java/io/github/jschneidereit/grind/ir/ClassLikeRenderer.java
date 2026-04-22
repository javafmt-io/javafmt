package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

final class ClassLikeRenderer {

    private ClassLikeRenderer() {}

    static Doc render(final ClassTree node, final String keyword, final Recursor recursor) {
        final var header = new StringBuilder();
        ModifierRenderer.renderModifiers(node.getModifiers(), header);
        header.append(keyword).append(" ").append(node.getSimpleName());

        final var members = node.getMembers().stream()
            .filter(m -> m instanceof VariableTree
                || (m instanceof MethodTree mt && !mt.getName().contentEquals("<init>")))
            .flatMap(m -> java.util.Optional.ofNullable(recursor.scan(m)).stream())
            .toList();

        if (members.isEmpty()) {
            header.append(" {}");
            return ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), new Doc.Text(header.toString()));
        }

        header.append(" {");
        return ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>concat(
                Stream.of(new Doc.Text(header.toString())),
                members.stream()
                    .flatMap(m -> Stream.<Doc>of(
                        new Doc.HardLine(),
                        new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), m)))
                    ))
                    .skip(1)
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        )));
    }
}
