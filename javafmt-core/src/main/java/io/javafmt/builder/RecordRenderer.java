package io.javafmt.builder;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import lombok.experimental.UtilityClass;

@UtilityClass
final class RecordRenderer {

    Doc render(final ClassTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var prefix = ModifierRenderer.modifiersText(node.getModifiers()) + "record " + node.getSimpleName();
        final var components = node.getMembers()
                .stream()
                .filter(RecordRenderer::isRecordComponent)
                .map(m -> (VariableTree) m)
                .toList();
        final var bodyMemberStream = node.getMembers().stream().filter(m -> !isRecordComponent(m));
        final var className = node.getSimpleName().toString();
        final var bodyMembers = TypeBodyRenderer.renderMembers(bodyMemberStream, className, recursor, attacher, RecordRenderer::renderConstructor);
        final Doc componentListDoc;
        if (components.isEmpty()) {
            componentListDoc = new Doc.Text("()");
        } else {
            componentListDoc = new Doc.Concat(
                    List.of(
                            new Doc.Text("("),
                            Doc.continuationIndent(
                                    new Doc.Concat(
                                            Stream.concat(
                                                    Stream.of(Doc.SOFT_LINE),
                                                    Doc.intersperse(
                                                            List.of(new Doc.Text(","), Doc.LINE),
                                                            components.stream().map(comp -> attacher.attach(comp, componentDoc(comp, recursor))))))),
                            new Doc.Text(")")));
        }
        final var prefixAndComponents = Doc.concatOf(
                Stream.of(new Doc.Text(prefix)),
                MethodRenderer.typeParamsPart(node.getTypeParameters(), recursor),
                Stream.of(componentListDoc));
        final var fullHeader = ClassLikeRenderer.buildTypeDeclHeader(prefixAndComponents, null, node.getImplementsClause(), false, recursor);
        final var headerDoc = new Doc.Group(fullHeader);
        return TypeBodyRenderer.withBody(node.getModifiers(), headerDoc, bodyMembers, attacher.trailingInside(node), recursor);
    }

    /**
     * Whether {@code member} is a record component from the header's {@code ()} list rather than
     * a body declaration. Java forbids non-static instance fields in a record body, so every
     * non-static {@link VariableTree} member must be a component — the two cannot be told apart
     * any other way.
     */
    private boolean isRecordComponent(final Tree member) {
        return member instanceof VariableTree v && !v.getModifiers().getFlags().contains(Modifier.STATIC);
    }

    private Doc componentDoc(final VariableTree comp, final Recursor recursor) {
        final var prefix = ModifierRenderer.annotationsText(comp.getModifiers(), recursor);
        return new Doc.Concat(List.of(new Doc.Text(prefix), recursor.scan(comp.getType()), new Doc.Text(" " + comp.getName())));
    }

    /** A record is the only type whose canonical constructor may be written in compact form. */
    private Doc renderConstructor(final MethodTree ctor, final String className, final Recursor recursor, final LeadingCommentAttacher attacher) {
        return recursor.isCompactConstructor(ctor)
                ? CompactConstructorRenderer.render(ctor, className, recursor, attacher)
                : ConstructorRenderer.render(ctor, className, recursor, attacher);
    }
}
