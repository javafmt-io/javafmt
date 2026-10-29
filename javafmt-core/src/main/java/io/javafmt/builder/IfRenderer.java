package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.StatementTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.CommentToken;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class IfRenderer {

    /**
     * javac wraps an {@code if} condition in {@code JCParens}, so the scanned condition already
     * includes its own outer parentheses — this method does not add them again.
     */
    Doc render(final IfTree node, final Recursor recursor) {
        final var expanded = renderExpanded(node, recursor);
        if (!writtenInTheCompactForm(node, recursor)) {
            return expanded;
        }
        // The one-line form only survives if it still fits at this indent, which is a print-time
        // question: flat gives back exactly what the author wrote, broken gives the block form.
        return new Doc.Group(new Doc.IfBreak(expanded, new Doc.Text(recursor.sourceOf(node))));
    }

    /**
     * Whether the author wrote this whole {@code if} — including every {@code else} and
     * {@code else if} after it — on one line in the compact form {@code if (c) {s;}}, with no
     * whitespace inside the braces and nothing but a single statement in each branch.
     *
     * <p>That exact shape is the only one javafmt keeps. Anything looser is a formatting accident
     * rather than a decision: {@code if (c) { s; }} with spaces is what a half-applied style leaves
     * behind, a braceless {@code if} is what {@code NeedBraces} has just rewritten, and two
     * statements on one line is what {@code OneStatementPerLine} exists to split. A comment
     * anywhere in the span rules it out too, since the rendering here is the source text verbatim
     * and a {@code //} comment would swallow the rest of the line.
     */
    private boolean writtenInTheCompactForm(final IfTree node, final Recursor recursor) {
        if (recursor.sourceSpan(node).chars().anyMatch(c -> c == '\n')) {
            return false;
        }
        final var source = recursor.sourceOf(node);
        if (!CommentScanner.scan(source).isEmpty()) {
            return false;
        }
        return spacedTheWayJavafmtWouldSpaceIt(source) && everyBranchIsACompactBlock(node, recursor);
    }

    /**
     * Whether the punctuation around the keywords is already javafmt's. The preserved form is
     * emitted as the author's source text verbatim, so whatever spacing it has survives — and
     * {@code if(c){s;}} coming back untouched from a formatter that writes {@code if (c)} everywhere
     * else is not preservation, it is a gap. Anything not spaced this way expands, where the normal
     * renderer puts it right.
     */
    private boolean spacedTheWayJavafmtWouldSpaceIt(final String source) {
        return source.startsWith("if (")
            && !source.contains("){")
            && !source.contains("}else")
            && !source.contains("else{")
            && !source.contains("  ");
    }

    private boolean everyBranchIsACompactBlock(final IfTree node, final Recursor recursor) {
        if (!isCompactBlock(node.getThenStatement(), recursor)) {
            return false;
        }
        return switch (node.getElseStatement()) {
            case null -> true;
            case IfTree elseIf -> everyBranchIsACompactBlock(elseIf, recursor);
            case StatementTree branch -> isCompactBlock(branch, recursor);
        };
    }

    private boolean isCompactBlock(final StatementTree statement, final Recursor recursor) {
        if (!(statement instanceof BlockTree block) || block.getStatements().size() != 1) {
            return false;
        }
        final var source = recursor.sourceOf(block);
        return source.length() > 2
            && source.startsWith("{")
            && source.endsWith("}")
            && !Character.isWhitespace(source.charAt(1))
            && !Character.isWhitespace(source.charAt(source.length() - 2));
    }

    private Doc renderExpanded(final IfTree node, final Recursor recursor) {
        final var condDoc = recursor.scan(node.getCondition());
        final var header = Doc.concat(new Doc.Text("if "), condDoc);
        final var thenStmts = BlockRenderer.blockStmts(node.getThenStatement(), recursor);
        final var braceLine = recursor.interior(node);
        if (node.getElseStatement() == null && braceLine.isEmpty()) {
            return BlockRenderer.buildBlock(header, thenStmts, List.of());
        }
        final var thenParts = thenParts(header, thenStmts, braceLine);
        if (node.getElseStatement() == null) {
            return Doc.concatOf(Stream.of(thenParts, Doc.closeBrace()));
        }
        final var elseJoint = elseJointParts(node, recursor);
        if (node.getElseStatement() instanceof IfTree elseIf) {
            final var elseIfDoc = Objects.requireNonNull(recursor.scan(elseIf));
            return Doc.concatOf(Stream.of(thenParts, Stream.<Doc>of(Doc.HARD_LINE, new Doc.Text("} else ")), elseJoint, Stream.of(elseIfDoc)));
        }
        final var elseStmts = BlockRenderer.blockStmts(node.getElseStatement(), recursor);
        return Doc.concatOf(
                Stream.of(
                        thenParts,
                        Stream.<Doc>of(Doc.HARD_LINE, new Doc.Text("} else ")),
                        elseJoint,
                        Stream.<Doc>of(new Doc.Text("{")),
                        elseStmts.stream().map(Doc::indentedLine),
                        Doc.closeBrace()));
    }

    /**
     * The header, the then-branch's opening brace, and its statements — with any comment the
     * author left on the brace's own line emitted right after the brace. Moving such a comment
     * down onto the first statement's line makes it read as annotating that statement rather
     * than the branch.
     */
    private Stream<Doc> thenParts(final Doc header, final List<Doc> thenStmts, final List<CommentToken> braceLine) {
        return Stream.of(
                Stream.of(header, new Doc.Text(" {")),
                braceLine.stream().flatMap(c -> Stream.of(new Doc.Text(" "), CommentDocs.renderComment(c))),
                thenStmts.stream().map(Doc::indentedLine))
                .flatMap(Function.identity());
    }

    /**
     * Whatever the author wrote between the then-branch and the else-branch, rendered where they
     * wrote it. A block comment there annotates the branch that follows, so hoisting it above the
     * whole statement — which is what happened before the attacher gave the else joint a slot of
     * its own — inverts its meaning.
     */
    private Stream<Doc> elseJointParts(final IfTree node, final Recursor recursor) {
        return recursor.tail(node)
                .stream()
                .flatMap(c -> Stream.of(CommentDocs.renderComment(c), c.text().startsWith("//") ? Doc.HARD_LINE : new Doc.Text(" ")));
    }
}
