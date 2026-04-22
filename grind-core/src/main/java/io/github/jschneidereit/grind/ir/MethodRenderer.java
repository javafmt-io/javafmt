package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.MethodTree;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

final class MethodRenderer {

    static @Nullable Doc render(final MethodTree node, final Recursor recursor) {
        if (node.getName().contentEquals("<init>")) {
            return null;
        }
        final var annotations = node.getModifiers().getAnnotations();
        final var inlineAnnotation = annotations.size() == 1 && annotations.get(0).getArguments().isEmpty();

        final var sb = new StringBuilder();
        if (inlineAnnotation) {
            ModifierRenderer.renderAnnotations(node.getModifiers(), sb);
        }
        ModifierRenderer.renderModifiers(node.getModifiers(), sb);
        if (node.getReturnType() != null) {
            sb.append(node.getReturnType());
            sb.append(" ");
        }
        sb.append(node.getName());
        sb.append("(");
        final var params = node.getParameters().stream()
            .map(param -> param.getType() + " " + param.getName())
            .collect(Collectors.joining(", "));
        sb.append(params);
        sb.append(")");

        final Doc signatureDoc;
        if (node.getBody() != null) {
            final var stmts = node.getBody().getStatements();
            if (stmts.isEmpty()) {
                sb.append(" {}");
                signatureDoc = new Doc.Text(sb.toString());
            } else {
                signatureDoc = new Doc.Concat(Stream.<Doc>concat(
                    Stream.<Doc>concat(
                        Stream.of(new Doc.Text(sb + " {")),
                        stmts.stream()
                            .flatMap(stmt -> Optional.ofNullable(recursor.scan(stmt)).stream())
                            .map(stmtDoc -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), stmtDoc))))
                    ),
                    Stream.of(new Doc.HardLine(), new Doc.Text("}"))
                ));
            }
        } else {
            sb.append(";");
            signatureDoc = new Doc.Text(sb.toString());
        }

        return inlineAnnotation ? signatureDoc : ModifierRenderer.prependOwnLineAnnotations(node.getModifiers(), signatureDoc);
    }

    private MethodRenderer() {}
}
