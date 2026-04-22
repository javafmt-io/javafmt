package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

final class MethodRenderer {

    static @Nullable Doc render(final MethodTree node, final Recursor recursor) {
        if (node.getName().contentEquals("<init>")) {
            return null;
        }
        final var annotations = node.getModifiers().getAnnotations();
        final var inlineAnnotation = annotations.size() == 1 && annotations.get(0).getArguments().isEmpty();

        final var leading = new StringBuilder();
        if (inlineAnnotation) {
            ModifierRenderer.renderAnnotations(node.getModifiers(), leading);
        }
        ModifierRenderer.renderModifiers(node.getModifiers(), leading);
        if (node.getReturnType() != null) {
            leading.append(node.getReturnType());
            leading.append(" ");
        }
        leading.append(node.getName());

        final var signature = renderSignature(leading.toString(), node.getParameters(), node.getThrows());
        final var signatureDoc = appendBody(signature, node, recursor);

        return inlineAnnotation ? signatureDoc : ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), signatureDoc);
    }

    private static Doc renderSignature(
            final String leading,
            final List<? extends VariableTree> params,
            final List<? extends ExpressionTree> throwsList) {
        if (params.isEmpty() && throwsList.isEmpty()) {
            return new Doc.Text(leading + "()");
        }
        final Doc paramsPart = params.isEmpty()
            ? new Doc.Text(leading + "()")
            : buildParamsPart(leading, params);
        if (throwsList.isEmpty()) {
            return new Doc.Group(paramsPart);
        }
        return new Doc.Group(new Doc.Concat(List.of(paramsPart, buildThrowsTail(throwsList))));
    }

    private static Doc buildParamsPart(final String leading, final List<? extends VariableTree> params) {
        final var interior = new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>of(new Doc.SoftLine()),
            params.stream()
                .<Doc>map(p -> new Doc.Text(p.getType() + " " + p.getName()))
                .flatMap(d -> Stream.<Doc>of(new Doc.Text(","), new Doc.Line(), d))
                .skip(2)
        ));
        return new Doc.Concat(List.of(
            new Doc.Text(leading + "("),
            new Doc.Indent(interior),
            new Doc.SoftLine(),
            new Doc.Text(")")
        ));
    }

    private static Doc buildThrowsTail(final List<? extends ExpressionTree> throwsList) {
        final var typesInterior = new Doc.Concat(throwsList.stream()
            .<Doc>map(e -> new Doc.Text(e.toString()))
            .flatMap(d -> Stream.<Doc>of(new Doc.Text(","), new Doc.Line(), d))
            .skip(2));
        return new Doc.Indent(new Doc.Concat(List.of(
            new Doc.Line(),
            new Doc.Text("throws "),
            new Doc.Group(new Doc.Indent(typesInterior))
        )));
    }

    private static Doc appendBody(final Doc header, final MethodTree node, final Recursor recursor) {
        if (node.getBody() == null) {
            return new Doc.Concat(List.of(header, new Doc.Text(";")));
        }
        final var stmts = node.getBody().getStatements();
        if (stmts.isEmpty()) {
            return new Doc.Concat(List.of(header, new Doc.Text(" {}")));
        }
        return new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>concat(
                Stream.of(header, new Doc.Text(" {")),
                stmts.stream()
                    .flatMap(stmt -> Optional.ofNullable(recursor.scan(stmt)).stream())
                    .map(stmtDoc -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), stmtDoc))))
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        ));
    }

    private MethodRenderer() {}
}
