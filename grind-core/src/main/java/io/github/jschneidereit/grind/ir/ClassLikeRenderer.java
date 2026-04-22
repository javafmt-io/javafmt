package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

import io.github.jschneidereit.grind.GrindConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

final class ClassLikeRenderer {

    static Doc render(final ClassTree node, final String keyword, final Recursor recursor, final GrindConfig config) {
        final var modifiers = new StringBuilder();
        ModifierRenderer.renderModifiers(node.getModifiers(), modifiers);
        final var prefix = modifiers.toString() + keyword + " " + node.getSimpleName();

        final var isInterface = node.getKind() == Tree.Kind.INTERFACE;
        final var superclass = isInterface ? null : node.getExtendsClause();
        final var interfaces = node.getImplementsClause();
        final var headerDoc = buildTypeDeclHeader(new Doc.Text(prefix), superclass, interfaces, isInterface);

        final var className = node.getSimpleName().toString();

        var memberStream = node.getMembers().stream()
            .filter(m -> m instanceof VariableTree || m instanceof MethodTree);

        if (config.reorderMembers()) {
            memberStream = memberStream.sorted(Comparator.comparingInt(MemberGrouper::group));
        }

        final var members = memberStream
            .flatMap(m -> Optional.ofNullable(renderMember(m, className, recursor)).stream())
            .toList();

        if (members.isEmpty()) {
            return ModifierRenderer.prependOwnLineAnnotations(
                node.getModifiers(),
                new Doc.Concat(List.of(headerDoc, new Doc.Text(" {}"))));
        }

        return ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>concat(
                Stream.of(headerDoc, new Doc.Text(" {")),
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

    static Doc buildTypeDeclHeader(
            final Doc prefix,
            final @Nullable Tree superclass,
            final List<? extends Tree> interfaces,
            final boolean interfacesKeywordIsExtends) {
        if (superclass == null && interfaces.isEmpty()) {
            return prefix;
        }
        final var parts = new ArrayList<Doc>();
        parts.add(prefix);
        if (superclass != null) {
            parts.add(new Doc.Indent(new Doc.Concat(List.of(
                new Doc.Line(),
                new Doc.Text("extends " + superclass)
            ))));
        }
        if (!interfaces.isEmpty()) {
            final var keyword = interfacesKeywordIsExtends ? "extends " : "implements ";
            final var typesInterior = new Doc.Concat(interfaces.stream()
                .<Doc>map(t -> new Doc.Text(t.toString()))
                .flatMap(d -> Stream.<Doc>of(new Doc.Text(","), new Doc.Line(), d))
                .skip(2));
            parts.add(new Doc.Indent(new Doc.Concat(List.of(
                new Doc.Line(),
                new Doc.Text(keyword),
                new Doc.Group(new Doc.Indent(typesInterior))
            ))));
        }
        return new Doc.Group(new Doc.Concat(parts));
    }

    private static @Nullable Doc renderMember(
            final Tree member, final String className, final Recursor recursor) {
        if (member instanceof MethodTree mt && mt.getName().contentEquals("<init>")) {
            return ConstructorRenderer.render(mt, className, recursor);
        }
        return recursor.scan(member);
    }

    private ClassLikeRenderer() {}
}
