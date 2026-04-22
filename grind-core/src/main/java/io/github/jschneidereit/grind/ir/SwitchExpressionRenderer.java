package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

final class SwitchExpressionRenderer {

    static Doc renderSwitch(final SwitchExpressionTree node, final Recursor recursor) {
        return renderSwitchLike(node.getExpression().toString(), node.getCases(), recursor);
    }

    static Doc renderSwitch(final SwitchTree node, final Recursor recursor) {
        return renderSwitchLike(node.getExpression().toString(), node.getCases(), recursor);
    }

    private static Doc renderSwitchLike(
            final String selectorWithParens,
            final List<? extends CaseTree> cases,
            final Recursor recursor) {
        final var caseDocs = cases.stream()
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
        final Doc bodyDoc;
        if (body instanceof StatementTree) {
            final var scanned = recursor.scan(body);
            bodyDoc = scanned != null ? scanned : new Doc.Text(body.toString().strip());
        } else {
            bodyDoc = new Doc.Text(body + ";");
        }
        return new Doc.Group(new Doc.Concat(List.of(
            new Doc.Text(prefix + " ->"),
            new Doc.IfBreak(
                new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), bodyDoc))),
                new Doc.Concat(List.of(new Doc.Text(" "), bodyDoc))
            )
        )));
    }

    private SwitchExpressionRenderer() {}
}
