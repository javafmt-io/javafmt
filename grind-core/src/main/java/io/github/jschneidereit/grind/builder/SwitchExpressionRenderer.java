package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import io.github.jschneidereit.grind.doc.Doc;
import io.github.jschneidereit.grind.doc.LeadingCommentAttacher;

final class SwitchExpressionRenderer {

    static Doc renderSwitch(final SwitchExpressionTree node, final Recursor recursor) {
        return renderSwitchLike(recursor.scan(node.getExpression()), node.getCases(), recursor);
    }

    static Doc renderSwitch(final SwitchTree node, final Recursor recursor) {
        return renderSwitchLike(recursor.scan(node.getExpression()), node.getCases(), recursor);
    }

    private static Doc renderSwitchLike(
            final Doc selectorWithParens,
            final List<? extends CaseTree> cases,
            final Recursor recursor) {
        final var caseDocs = cases.stream()
            .<Doc>map(recursor::scan)
            .toList();
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.<Doc>of(new Doc.Text("switch "), selectorWithParens, new Doc.Text(" {")),
                caseDocs.stream()
                    .map(d -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), d))))
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        ));
    }

    static Doc renderCase(final CaseTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var prefixDoc = renderLabels(node, recursor);
        if (node.getCaseKind() != CaseTree.CaseKind.RULE) {
            return renderColonForm(prefixDoc, node, recursor);
        }
        final var body = node.getBody();
        if (body == null) {
            return new Doc.Concat(List.of(prefixDoc, new Doc.Text(" ->")));
        }
        if (body instanceof BlockTree blockBody) {
            final var stmts = blockBody.getStatements().stream()
                .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
                .toList();
            return BlockRenderer.buildBlock(
                new Doc.Concat(List.of(prefixDoc, new Doc.Text(" ->"))),
                stmts,
                attacher.interior(blockBody));
        }
        final Doc bodyDoc = body instanceof StatementTree
            ? recursor.scan(body)
            : new Doc.Text(body + ";");
        return new Doc.Group(new Doc.Concat(List.of(
            prefixDoc,
            new Doc.Text(" ->"),
            new Doc.IfBreak(
                new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), bodyDoc))),
                new Doc.Concat(List.of(new Doc.Text(" "), bodyDoc))
            )
        )));
    }

    private static Doc renderLabels(final CaseTree node, final Recursor recursor) {
        final var isDefault = node.getLabels().stream().anyMatch(l -> l instanceof DefaultCaseLabelTree);
        if (isDefault) {
            return new Doc.Text("default");
        }
        final var labelParts = new java.util.ArrayList<Doc>();
        labelParts.add(new Doc.Text("case "));
        Doc.intersperse(new Doc.Text(", "), node.getLabels().stream()
            .<Doc>map(recursor::scan))
            .forEach(labelParts::add);
        return new Doc.Concat(labelParts);
    }

    private static Doc renderColonForm(final Doc prefixDoc, final CaseTree node, final Recursor recursor) {
        final var prefix = new Doc.Concat(List.of(prefixDoc, new Doc.Text(":")));
        final var stmts = node.getStatements().stream()
            .<Doc>flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
            .toList();
        if (stmts.isEmpty()) {
            return prefix;
        }
        return new Doc.Concat(Stream.concat(
            Stream.<Doc>of(prefix),
            stmts.stream().map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s))))));
    }

    private SwitchExpressionRenderer() {}
}
