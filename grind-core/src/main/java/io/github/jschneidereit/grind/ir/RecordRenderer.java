package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;

import java.util.List;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

final class RecordRenderer {

    static Doc render(final ClassTree node, final Recursor recursor) {
        final var prefix = new StringBuilder();
        ModifierRenderer.renderModifiers(node.getModifiers(), prefix);
        prefix.append("record ").append(node.getSimpleName());

        // Java forbids non-static instance fields in a record body, so non-static
        // VariableTrees are always record components (from the header's `()` list).
        final var components = node.getMembers().stream()
            .filter(m -> m instanceof VariableTree v
                && !v.getModifiers().getFlags().contains(Modifier.STATIC))
            .map(m -> (VariableTree) m)
            .toList();

        final var bodyMembers = node.getMembers().stream()
            .filter(m -> !(m instanceof VariableTree v
                && !v.getModifiers().getFlags().contains(Modifier.STATIC))
                && !(m instanceof MethodTree mt && mt.getName().contentEquals("<init>")))
            .flatMap(m -> java.util.Optional.ofNullable(recursor.scan(m)).stream())
            .toList();

        final Doc componentListDoc;
        if (components.isEmpty()) {
            componentListDoc = new Doc.Text("()");
        } else {
            componentListDoc = new Doc.Concat(List.of(
                new Doc.Text("("),
                new Doc.Indent(new Doc.Concat(Stream.concat(
                    Stream.<Doc>of(new Doc.SoftLine()),
                    components.stream()
                        .flatMap(comp -> Stream.<Doc>of(
                            new Doc.Text(","),
                            new Doc.Line(),
                            new Doc.Text(comp.getType() + " " + comp.getName())
                        ))
                        .skip(2)
                ))),
                new Doc.SoftLine(),
                new Doc.Text(")")
            ));
        }

        if (bodyMembers.isEmpty()) {
            return ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text(prefix.toString()),
                componentListDoc,
                new Doc.Text(" {}")
            ))));
        }

        return ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>concat(
                Stream.of(new Doc.Text(prefix.toString()), componentListDoc, new Doc.Text(" {")),
                bodyMembers.stream()
                    .flatMap(m -> Stream.<Doc>of(
                        new Doc.HardLine(),
                        new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), m)))
                    ))
                    .skip(1)
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        )));
    }

    private RecordRenderer() {}
}
