package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;

import io.github.jschneidereit.grind.GrindConfig;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class EnumRenderer {

    static Doc render(final ClassTree node, final Recursor recursor, final GrindConfig config) {
        final var header = new StringBuilder();
        ModifierRenderer.renderModifiers(node.getModifiers(), header);
        header.append("enum ").append(node.getSimpleName());

        final var constants = node.getMembers().stream()
            .filter(m -> m instanceof VariableTree v && v.getInitializer() instanceof NewClassTree)
            .map(m -> (VariableTree) m)
            .sorted(Comparator.comparing(v -> v.getName().toString()))
            .toList();

        var bodyMemberStream = node.getMembers().stream()
            .filter(m -> !(m instanceof VariableTree v && v.getInitializer() instanceof NewClassTree)
                && !(m instanceof MethodTree mt && mt.getName().contentEquals("<init>")));

        if (config.reorderMembers()) {
            bodyMemberStream = bodyMemberStream.sorted(Comparator.comparingInt(MemberGrouper::group));
        }

        final var bodyMembers = bodyMemberStream
            .flatMap(m -> java.util.Optional.ofNullable(recursor.scan(m)).stream())
            .toList();

        if (constants.isEmpty() && bodyMembers.isEmpty()) {
            header.append(" {}");
            return ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), new Doc.Text(header.toString()));
        }

        header.append(" {");

        if (bodyMembers.isEmpty()) {
            // Group: single-line if fits within 150, multi-line with trailing comma if not.
            // Intersperse constants with ", " (flat) or ",\n    " (break), trailing comma only on break.
            final var constantsInner = new Doc.Indent(new Doc.Concat(Stream.concat(
                Stream.<Doc>of(new Doc.Line()),
                Stream.concat(
                    constants.stream()
                        .<Doc>map(v -> new Doc.Text(v.getName().toString()))
                        .flatMap(d -> Stream.<Doc>of(new Doc.Text(","), new Doc.Line(), d))
                        .skip(2),
                    Stream.<Doc>of(new Doc.IfBreak(new Doc.Text(","), new Doc.Text("")))
                )
            )));
            return ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text(header.toString()),
                constantsInner,
                new Doc.Line(),
                new Doc.Text("}")
            ))));
        }

        // Has body members: always multi-line; last constant uses ";" as the required
        // separator before body declarations, all others use ",".
        final var n = constants.size();
        final var constantsDocs = IntStream.range(0, n)
            .<Doc>mapToObj(i -> new Doc.Indent(new Doc.Concat(List.of(
                new Doc.HardLine(),
                new Doc.Text(constants.get(i).getName() + (i < n - 1 ? "," : ";"))
            ))));

        final Stream<Doc> bodyMembersDocs = Stream.concat(
            Stream.<Doc>of(new Doc.HardLine()),
            bodyMembers.stream()
                .flatMap(m -> Stream.<Doc>of(
                    new Doc.HardLine(),
                    new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), m)))
                ))
                .skip(1)
        );

        return ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.concat(
                    Stream.of(new Doc.Text(header.toString())),
                    constantsDocs
                ),
                bodyMembersDocs
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        )));
    }

    private EnumRenderer() {}
}
