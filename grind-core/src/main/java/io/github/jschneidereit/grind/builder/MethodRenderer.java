package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import io.github.jschneidereit.grind.doc.Doc;
import io.github.jschneidereit.grind.doc.LeadingCommentAttacher;

final class MethodRenderer {

    static @Nullable Doc render(final MethodTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        if (node.getName().contentEquals("<init>")) {
            return null;
        }
        final var annotations = node.getModifiers().getAnnotations();
        final var inlineAnnotation = annotations.size() == 1 && annotations.get(0).getArguments().isEmpty();

        final var modifiersText = new StringBuilder();
        if (inlineAnnotation) {
            ModifierRenderer.renderAnnotations(node.getModifiers(), modifiersText);
        }
        ModifierRenderer.renderModifiers(node.getModifiers(), modifiersText);

        final var afterTypeParams = new StringBuilder();
        if (node.getReturnType() != null) {
            afterTypeParams.append(node.getReturnType());
            afterTypeParams.append(" ");
        }
        afterTypeParams.append(node.getName());

        final var leading = buildLeading(modifiersText.toString(), node.getTypeParameters(), afterTypeParams.toString(), recursor);

        final var signature = renderSignature(leading, node.getParameters(), node.getThrows(), attacher, recursor);
        final var signatureDoc = appendBody(signature, node, recursor, attacher);

        return inlineAnnotation ? signatureDoc : ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), signatureDoc);
    }

    static Doc buildLeading(
            final String modifiers,
            final List<? extends TypeParameterTree> typeParams,
            final String afterTypeParams,
            final Recursor recursor) {
        if (typeParams.isEmpty()) {
            return new Doc.Text(modifiers + afterTypeParams);
        }
        return new Doc.Concat(List.of(
            new Doc.Text(modifiers),
            renderTypeParams(typeParams, recursor),
            new Doc.Text(" " + afterTypeParams)
        ));
    }

    private static Doc renderTypeParams(final List<? extends TypeParameterTree> typeParams, final Recursor recursor) {
        final var interior = new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>of(new Doc.SoftLine()),
            Doc.intersperse(List.of(new Doc.Text(","), new Doc.Line()), typeParams.stream()
                .<Doc>map(recursor::scan))
        ));
        return new Doc.Group(new Doc.Concat(List.of(
            new Doc.Text("<"),
            new Doc.Indent(interior),
            new Doc.SoftLine(),
            new Doc.Text(">")
        )));
    }

    static Doc renderSignature(
            final Doc leading,
            final List<? extends VariableTree> params,
            final List<? extends ExpressionTree> throwsList,
            final LeadingCommentAttacher attacher,
            final Recursor recursor) {
        if (params.isEmpty() && throwsList.isEmpty()) {
            return new Doc.Concat(List.of(leading, new Doc.Text("()")));
        }
        final Doc paramsPart = params.isEmpty()
            ? new Doc.Concat(List.of(leading, new Doc.Text("()")))
            : buildParamsPart(leading, params, attacher, recursor);
        if (throwsList.isEmpty()) {
            return new Doc.Group(paramsPart);
        }
        return new Doc.Group(new Doc.Concat(List.of(paramsPart, buildThrowsTail(throwsList, recursor))));
    }

    private static Doc buildParamsPart(
            final Doc leading,
            final List<? extends VariableTree> params,
            final LeadingCommentAttacher attacher,
            final Recursor recursor) {
        final var interior = new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>of(new Doc.SoftLine()),
            Doc.intersperse(List.of(new Doc.Text(","), new Doc.Line()), params.stream()
                .<Doc>map(p -> attacher.attach(p, renderParam(p, recursor))))
        ));
        return new Doc.Concat(List.of(
            leading,
            new Doc.Text("("),
            new Doc.Indent(interior),
            new Doc.SoftLine(),
            new Doc.Text(")")
        ));
    }

    private static Doc renderParam(final VariableTree p, final Recursor recursor) {
        final var prefix = new StringBuilder();
        ModifierRenderer.renderAnnotations(p.getModifiers(), prefix);
        ModifierRenderer.renderModifiers(p.getModifiers(), prefix);
        if (recursor.isVarargs(p) && p.getType() instanceof ArrayTypeTree arr) {
            return new Doc.Concat(List.of(
                new Doc.Text(prefix.toString()),
                recursor.scan(arr.getType()),
                new Doc.Text("... " + p.getName())
            ));
        }
        final var typeDoc = p.getType() == null ? new Doc.Text("var") : recursor.scan(p.getType());
        return new Doc.Concat(List.of(
            new Doc.Text(prefix.toString()),
            typeDoc,
            new Doc.Text(" " + p.getName())
        ));
    }

    private static Doc buildThrowsTail(final List<? extends ExpressionTree> throwsList, final Recursor recursor) {
        final var typesInterior = new Doc.Concat(Doc.intersperse(
            List.of(new Doc.Text(","), new Doc.Line()),
            throwsList.stream().<Doc>map(recursor::scan)));
        return new Doc.Indent(new Doc.Concat(List.of(
            new Doc.Line(),
            new Doc.Text("throws "),
            new Doc.Group(new Doc.Indent(typesInterior))
        )));
    }

    static Doc appendBody(final Doc header, final MethodTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        if (node.getBody() == null) {
            return new Doc.Concat(List.of(header, new Doc.Text(";")));
        }
        final var body = node.getBody();
        final var stmts = body.getStatements();
        final var interior = attacher.interior(body);
        final var tail = attacher.tail(body);
        if (stmts.isEmpty() && interior.isEmpty() && tail.isEmpty()) {
            return new Doc.Concat(List.of(header, new Doc.Text(" {}")));
        }
        final var stmtDocs = stmts.stream()
            .flatMap(stmt -> Optional.ofNullable(recursor.scan(stmt)).stream())
            .toList();
        final var interiorDocs = interior.stream().<Doc>map(CommentDocs::renderComment).toList();
        final var tailDocs = tail.stream().<Doc>map(CommentDocs::renderComment).toList();
        final var all = Stream.of(interiorDocs, stmtDocs, tailDocs).flatMap(List::stream).toList();
        return new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>concat(
                Stream.of(header, new Doc.Text(" {")),
                all.stream()
                    .map(d -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), d))))
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        ));
    }

    private MethodRenderer() {}
}
