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
        final var modifiers = new StringBuilder();
        ModifierRenderer.renderModifiers(node.getModifiers(), modifiers);
        final var prefix = modifiers.toString() + "enum " + node.getSimpleName();
        final var headerDoc = ClassLikeRenderer.buildTypeDeclHeader(
            new Doc.Text(prefix), null, node.getImplementsClause(), false);

        final var constants = node.getMembers().stream()
            .filter(m -> m instanceof VariableTree v && v.getInitializer() instanceof NewClassTree)
            .map(m -> (VariableTree) m)
            .sorted(Comparator.comparing(v -> v.getName().toString()))
            .toList();

        final var className = node.getSimpleName().toString();

        var bodyMemberStream = node.getMembers().stream()
            .filter(m -> !(m instanceof VariableTree v && v.getInitializer() instanceof NewClassTree));

        if (config.reorderMembers()) {
            bodyMemberStream = bodyMemberStream.sorted(Comparator.comparingInt(MemberGrouper::group));
        }

        final var bodyMembers = bodyMemberStream
            .flatMap(m -> java.util.Optional.ofNullable(renderBodyMember(m, className, recursor)).stream())
            .toList();

        if (constants.isEmpty() && bodyMembers.isEmpty()) {
            return ModifierRenderer.prependOwnLineAnnotations(
                node.getModifiers(),
                new Doc.Concat(List.of(headerDoc, new Doc.Text(" {}"))));
        }

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
                headerDoc,
                new Doc.Text(" {"),
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
                    Stream.of(headerDoc, new Doc.Text(" {")),
                    constantsDocs
                ),
                bodyMembersDocs
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        )));
    }

    private static @org.jspecify.annotations.Nullable Doc renderBodyMember(
            final com.sun.source.tree.Tree member, final String className, final Recursor recursor) {
        if (member instanceof MethodTree mt && mt.getName().contentEquals("<init>")) {
            return ConstructorRenderer.render(mt, className, recursor);
        }
        return recursor.scan(member);
    }

    private EnumRenderer() {}
}
