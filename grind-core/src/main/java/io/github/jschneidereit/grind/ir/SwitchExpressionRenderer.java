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

    static Doc renderSwitch(final SwitchExpressionTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        return renderSwitchLike(node.getExpression().toString(), node.getCases(), recursor, attacher);
    }

    static Doc renderSwitch(final SwitchTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        return renderSwitchLike(node.getExpression().toString(), node.getCases(), recursor, attacher);
    }

    private static Doc renderSwitchLike(
            final String selectorWithParens,
            final List<? extends CaseTree> cases,
            final Recursor recursor,
            final LeadingCommentAttacher attacher) {
        final var caseDocs = cases.stream()
            .flatMap(c -> Optional.ofNullable(renderCase(c, recursor, attacher)).stream())
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

    static @Nullable Doc renderCase(final CaseTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        if (node.getCaseKind() != CaseTree.CaseKind.RULE) {
            return null;
        }
        final var isDefault = node.getLabels().stream().anyMatch(l -> l instanceof DefaultCaseLabelTree);
        final Doc prefixDoc;
        if (isDefault) {
            prefixDoc = new Doc.Text("default");
        } else {
            final var labelParts = new java.util.ArrayList<Doc>();
            labelParts.add(new Doc.Text("case "));
            node.getLabels().stream()
                .<Doc>map(recursor::scanOrText)
                .flatMap(d -> java.util.stream.Stream.<Doc>of(new Doc.Text(", "), d))
                .skip(1)
                .forEach(labelParts::add);
            prefixDoc = new Doc.Concat(labelParts);
        }

        final var body = node.getBody();
        if (body == null) {
            return null;
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
            ? recursor.scanOrText(body)
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

    private SwitchExpressionRenderer() {}
}
