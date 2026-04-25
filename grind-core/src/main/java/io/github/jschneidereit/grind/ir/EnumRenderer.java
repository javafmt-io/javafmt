package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.BlockTree;
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

    static Doc render(final ClassTree node, final Recursor recursor, final GrindConfig config, final LeadingCommentAttacher attacher) {
        final var modifiers = new StringBuilder();
        ModifierRenderer.renderModifiers(node.getModifiers(), modifiers);
        final var prefix = modifiers.toString() + "enum " + node.getSimpleName();
        final var headerDoc = ClassLikeRenderer.buildTypeDeclHeader(
            new Doc.Text(prefix), null, node.getImplementsClause(), false, recursor);

        final var constants = node.getMembers().stream()
            .filter(m -> m instanceof VariableTree v && v.getInitializer() instanceof NewClassTree)
            .map(m -> (VariableTree) m)
            .sorted(Comparator.comparing(v -> v.getName().toString()))
            .toList();

        final var className = node.getSimpleName().toString();

        final var bodyMemberStream = node.getMembers().stream()
            .filter(m -> !(m instanceof VariableTree v && v.getInitializer() instanceof NewClassTree));

        final var bodyMembers = MemberReorderer.reorder(bodyMemberStream, config, false, recursor)
            .flatMap(m -> java.util.Optional.ofNullable(renderBodyMember(m, className, recursor, attacher)).stream())
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
                    Doc.intersperse(List.of(new Doc.Text(","), new Doc.Line()), constants.stream()
                        .map(v -> attacher.attach(v, renderConstant(v, recursor)))),
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
                attacher.attach(constants.get(i), new Doc.Concat(List.of(
                    renderConstant(constants.get(i), recursor),
                    new Doc.Text(i < n - 1 ? "," : ";")
                )))
            ))));

        final Stream<Doc> bodyMembersDocs = Stream.concat(
            Stream.<Doc>of(new Doc.HardLine()),
            Doc.intersperse(new Doc.HardLine(), bodyMembers.stream()
                .<Doc>map(m -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), m)))))
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

    private static Doc renderConstant(final VariableTree v, final Recursor recursor) {
        final var name = v.getName().toString();
        if (!(v.getInitializer() instanceof NewClassTree nc) || nc.getArguments().isEmpty()) {
            return new Doc.Text(name);
        }
        final var argsInterior = Doc.intersperse(new Doc.Text(", "), nc.getArguments().stream()
            .<Doc>map(recursor::scan));
        return new Doc.Concat(Stream.concat(
            Stream.concat(Stream.<Doc>of(new Doc.Text(name + "(")), argsInterior),
            Stream.<Doc>of(new Doc.Text(")"))));
    }

    private static @org.jspecify.annotations.Nullable Doc renderBodyMember(
            final com.sun.source.tree.Tree member, final String className, final Recursor recursor,
            final LeadingCommentAttacher attacher) {
        if (member instanceof MethodTree mt && mt.getName().contentEquals("<init>")) {
            return attacher.attach(mt, ConstructorRenderer.render(mt, className, recursor, attacher));
        }
        if (member instanceof BlockTree bt) {
            return attacher.attach(bt, InitBlockRenderer.render(bt, recursor, attacher));
        }
        return recursor.scan(member);
    }

    private EnumRenderer() {}
}
