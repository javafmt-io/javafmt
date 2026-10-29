package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class SwitchExpressionRenderer {

    Doc renderSwitch(final SwitchExpressionTree node, final Recursor recursor) {
        return renderSwitchLike(recursor.scan(node.getExpression()), node.getCases(), recursor);
    }

    Doc renderSwitch(final SwitchTree node, final Recursor recursor) {
        return renderSwitchLike(recursor.scan(node.getExpression()), node.getCases(), recursor);
    }

    private Doc renderSwitchLike(final Doc selectorWithParens, final List<? extends CaseTree> cases, final Recursor recursor) {
        final var caseDocs = cases.stream().map(recursor::scan).map(Doc::indentedLine);
        return Doc.concatOf(Stream.of(Stream.of(new Doc.Text("switch "), selectorWithParens, new Doc.Text(" {")), caseDocs, Doc.closeBrace()));
    }

    Doc renderCase(final CaseTree node, final Recursor recursor) {
        final var prefixDoc = renderLabels(node, recursor);
        if (node.getCaseKind() != CaseTree.CaseKind.RULE) {
            return renderColonForm(prefixDoc, node, recursor);
        }
        final var body = node.getBody();
        if (body == null) {
            return Doc.concat(prefixDoc, new Doc.Text(" ->"));
        }
        if (body instanceof BlockTree blockBody) {
            final var stmts = blockBody.getStatements()
                    .stream()
                    .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
                    .toList();
            return BlockRenderer.buildBlock(Doc.concat(prefixDoc, new Doc.Text(" ->")), stmts, recursor.interior(blockBody));
        }
        final var bodyDoc = body instanceof StatementTree ? recursor.scan(body) : Doc.concat(recursor.scan(body), new Doc.Text(";"));
        if (mustHugArrow(bodyDoc)) {
            return Doc.concat(prefixDoc, new Doc.Text(" -> "), bodyDoc);
        }
        return new Doc.Group(
                Doc.concat(
                        prefixDoc,
                        new Doc.Text(" ->"),
                        new Doc.IfBreak(new Doc.Indent(Doc.concat(Doc.HARD_LINE, bodyDoc)), Doc.concat(new Doc.Text(" "), bodyDoc))));
    }

    /**
     * Whether {@code bodyDoc} cannot render flat — a lambda or anonymous class body with its own
     * block. Such a body hugs the arrow the same way a block-bodied arm's opening brace does,
     * rather than breaking after the arrow and indenting an already multi-line body.
     */
    private boolean mustHugArrow(final Doc bodyDoc) {
        return Doc.flatWidthOrBreak(bodyDoc) < 0;
    }

    /**
     * Renders the label list plus any {@code when} guard. The labels wrap one per line when they do
     * not fit: {@code java/util/Formatter.java} has a {@code case} with twenty of them on a
     * 331-character line, the widest in {@code java.base}.
     *
     * <p> {@code default} is only rendered bare
     * when it is the sole label — in a mixed list such as {@code case null, default} the
     * {@code case} keyword and every label are required, and collapsing the list to {@code default}
     * would silently drop the {@code null} label and with it the arm's NPE behavior.
     */
    private Doc renderLabels(final CaseTree node, final Recursor recursor) {
        final var labels = node.getLabels();
        final var head = labels.size() == 1 && labels.getFirst() instanceof DefaultCaseLabelTree
                ? Stream.<Doc>of(new Doc.Text("default"))
                : Stream.of(new Doc.Text("case "), labelList(labels, recursor));
        final var guard = node.getGuard();
        final var guardDoc = guard == null ? Stream.<Doc>empty() : Stream.of(new Doc.Text(" when "), recursor.scan(guard));
        return new Doc.Concat(Stream.concat(head, guardDoc));
    }

    /**
     * The labels themselves. A list of two or more gets the wrapping group; a single label is
     * rendered bare, because the group's continuation indent would be a second one on top of
     * whatever the label wraps with itself — a deconstruction pattern breaks over several lines
     * on its own and came out indented twice.
     */
    private Doc labelList(final List<? extends Tree> labels, final Recursor recursor) {
        return labels.size() == 1
                ? recursor.scan(labels.getFirst())
                : WrappableList.joined(List.of(new Doc.Text(","), Doc.LINE), labels.stream().map(recursor::scan));
    }

    private Doc renderColonForm(final Doc prefixDoc, final CaseTree node, final Recursor recursor) {
        final var prefix = Doc.concat(prefixDoc, new Doc.Text(":"));
        final var stmts = node.getStatements()
                .stream()
                .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
                .toList();
        if (stmts.isEmpty()) {
            return prefix;
        }
        if (node.getStatements().size() == 1 && node.getStatements().getFirst() instanceof BlockTree) {
            // K&R: a braced colon-form arm opens on the label's line, the same as every other block
            // in the file. Indenting the brace onto its own line is the one place javafmt did not.
            return Doc.concat(prefix, new Doc.Text(" "), stmts.getFirst());
        }
        return new Doc.Concat(Stream.concat(Stream.of(prefix), stmts.stream().map(Doc::indentedLine)));
    }
}
