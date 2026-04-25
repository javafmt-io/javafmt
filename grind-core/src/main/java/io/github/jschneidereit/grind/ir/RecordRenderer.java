package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

import io.github.jschneidereit.grind.GrindConfig;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

final class RecordRenderer {

    static Doc render(final ClassTree node, final Recursor recursor, final GrindConfig config, final LeadingCommentAttacher attacher) {
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

        var bodyMemberStream = node.getMembers().stream()
            .filter(m -> !(m instanceof VariableTree v
                && !v.getModifiers().getFlags().contains(Modifier.STATIC))
                && !(m instanceof MethodTree mt && mt.getName().contentEquals("<init>")));

        if (config.reorderMembers()) {
            bodyMemberStream = bodyMemberStream.sorted(Comparator.comparingInt(m -> MemberGrouper.group(m, false)));
        }

        final var bodyMembers = bodyMemberStream
            .flatMap(m -> java.util.Optional.ofNullable(renderBodyMember(m, recursor, attacher)).stream())
            .toList();

        final Doc componentListDoc;
        if (components.isEmpty()) {
            componentListDoc = new Doc.Text("()");
        } else {
            componentListDoc = new Doc.Concat(List.of(
                new Doc.Text("("),
                new Doc.Indent(new Doc.Concat(Stream.concat(
                    Stream.<Doc>of(new Doc.SoftLine()),
                    Doc.intersperse(List.of(new Doc.Text(","), new Doc.Line()), components.stream()
                        .map(comp -> attacher.attach(comp, new Doc.Text(comp.getType() + " " + comp.getName()))))
                ))),
                new Doc.SoftLine(),
                new Doc.Text(")")
            ));
        }

        final var prefixAndComponents = new Doc.Concat(List.of(
            new Doc.Text(prefix.toString()),
            componentListDoc
        ));
        final var fullHeader = ClassLikeRenderer.buildTypeDeclHeader(
            prefixAndComponents, null, node.getImplementsClause(), false, recursor);

        if (bodyMembers.isEmpty()) {
            return ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), new Doc.Group(new Doc.Concat(List.of(
                fullHeader,
                new Doc.Text(" {}")
            ))));
        }

        final var headerDoc = new Doc.Group(fullHeader);
        return ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>concat(
                Stream.of(headerDoc, new Doc.Text(" {")),
                Doc.intersperse(new Doc.HardLine(), bodyMembers.stream()
                    .<Doc>map(m -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), m)))))
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        )));
    }

    private static @org.jspecify.annotations.Nullable Doc renderBodyMember(
            final Tree member, final Recursor recursor, final LeadingCommentAttacher attacher) {
        if (member instanceof BlockTree bt) {
            return attacher.attach(bt, InitBlockRenderer.render(bt, recursor, attacher));
        }
        return recursor.scan(member);
    }

    private RecordRenderer() {}
}
