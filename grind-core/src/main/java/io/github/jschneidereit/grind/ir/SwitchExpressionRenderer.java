package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.SwitchExpressionTree;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

final class SwitchExpressionRenderer {

    private SwitchExpressionRenderer() {}

    static Doc renderSwitch(final SwitchExpressionTree node, final Recursor recursor) {
        // getExpression().toString() already includes surrounding parens, e.g. "(x)"
        final var selectorWithParens = node.getExpression().toString();
        final var caseDocs = node.getCases().stream()
            .flatMap(c -> Optional.ofNullable(renderCase(c, recursor)).stream())
            .toList();
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.<Doc>of(new Doc.Text("switch " + selectorWithParens + " {")),
                caseDocs.stream()
                    .map(d -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), d))))
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        ));
    }

    static @Nullable Doc renderCase(final CaseTree node, final Recursor recursor) {
        if (node.getCaseKind() != CaseTree.CaseKind.RULE) {
            return null;
        }
        final var isDefault = node.getLabels().stream().anyMatch(l -> l instanceof DefaultCaseLabelTree);
        final var labelStr = node.getLabels().stream()
            .map(Object::toString)
            .collect(Collectors.joining(", "));
        final var prefix = isDefault ? "default" : "case " + labelStr;

        final var body = node.getBody();
        if (body == null) {
            return null;
        }
        if (body instanceof BlockTree blockBody) {
            final var stmts = blockBody.getStatements().stream()
                .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
                .toList();
            return BlockRenderer.buildBlock(prefix + " ->", stmts);
        }
        return new Doc.Text(prefix + " -> " + body + ";");
    }
}
