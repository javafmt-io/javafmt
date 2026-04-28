package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

import io.github.jschneidereit.grind.GrindConfig;

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

        final var bodyMemberStream = node.getMembers().stream()
            .filter(m -> !(m instanceof VariableTree v
                && !v.getModifiers().getFlags().contains(Modifier.STATIC)));

        final var className = node.getSimpleName().toString();
        final var bodyMembers = MemberReorderer.reorder(bodyMemberStream, config, false, recursor)
            .flatMap(m -> java.util.Optional.ofNullable(renderBodyMember(m, className, recursor, attacher)).stream())
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
                        .map(comp -> attacher.attach(comp, componentDoc(comp, recursor))))
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

    private static Doc componentDoc(final VariableTree comp, final Recursor recursor) {
        final var prefix = new StringBuilder();
        ModifierRenderer.renderAnnotations(comp.getModifiers(), prefix);
        return new Doc.Concat(List.of(
            new Doc.Text(prefix.toString()),
            recursor.scan(comp.getType()),
            new Doc.Text(" " + comp.getName())
        ));
    }

    private static @org.jspecify.annotations.Nullable Doc renderBodyMember(
            final Tree member, final String className, final Recursor recursor, final LeadingCommentAttacher attacher) {
        if (member instanceof MethodTree mt && mt.getName().contentEquals("<init>")) {
            return attacher.attach(mt, recursor.isCompactConstructor(mt)
                ? CompactConstructorRenderer.render(mt, className, recursor, attacher)
                : ConstructorRenderer.render(mt, className, recursor, attacher));
        }
        if (member instanceof BlockTree bt) {
            return attacher.attach(bt, InitBlockRenderer.render(bt, recursor, attacher));
        }
        return recursor.scan(member);
    }

    private RecordRenderer() {}
}
