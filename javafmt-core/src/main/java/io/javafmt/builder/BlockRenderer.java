package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.StatementTree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import io.javafmt.parser.CommentToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class BlockRenderer {

    List<Doc> blockStmts(final StatementTree stmt, final Recursor recursor, final LeadingCommentAttacher attacher) {
        if (stmt instanceof BlockTree block) {
            final var stmtDocs = statementDocs(block.getStatements(), recursor);
            final var tailDocs = attacher.trailingInside(block).stream().<Doc>map(CommentDocs::renderComment);
            return Stream.concat(stmtDocs, tailDocs).toList();
        }
        recursor.emitWarning("added braces around single-statement body", stmt);
        return Optional.ofNullable(recursor.scan(stmt)).map(List::of).orElse(List.of());
    }

    /**
     * Each statement's Doc, with a blank line kept in front of any statement the author separated
     * from the one before. The paragraph breaks in a method body are the author's own structure;
     * deleting every one of them, which is what javafmt used to do, runs the whole body together.
     * A run of several collapses to one, since only the separation carries meaning, and a blank
     * line right after the opening brace or before the closing one is never kept — those are
     * javafmt's to decide.
     */
    Stream<Doc> statementDocs(final List<? extends StatementTree> statements, final Recursor recursor) {
        return IntStream.range(0, statements.size())
                .mapToObj(
                        i -> Optional.ofNullable(recursor.scan(statements.get(i)))
                                .map(
                                        doc -> i > 0 && recursor.blankLineBetween(statements.get(i - 1), statements.get(i))
                                                ? new Doc.Concat(List.of(Doc.HARD_LINE, doc))
                                                : doc))
                .flatMap(Optional::stream);
    }

    Doc buildBlock(final String header, final List<Doc> stmts, final List<CommentToken> trailing) {
        return buildBlock(new Doc.Text(header), stmts, trailing);
    }

    /**
     * A block, with {@code trailing} — the comments nothing inside it follows — written after the
     * statements rather than before them. An empty block has only those comments so the order does
     * not show there, but a static initializer ending in a block of notes had them hoisted above
     * its first statement, which is not where the author left them.
     */
    Doc buildBlock(final Doc header, final List<Doc> stmts, final List<CommentToken> trailing) {
        if (stmts.isEmpty() && trailing.isEmpty()) {
            return new Doc.Concat(List.of(header, new Doc.Text(" {}")));
        }
        final var all = Stream.concat(stmts.stream(), trailing.stream().<Doc>map(CommentDocs::renderComment));
        return Doc.concatOf(Stream.of(blockParts(header, all), Doc.closeBrace()));
    }

    Doc buildBlock(final List<Doc> stmts, final List<CommentToken> trailing) {
        if (stmts.isEmpty() && trailing.isEmpty()) {
            return new Doc.Text("{}");
        }
        final var all = Stream.concat(stmts.stream(), trailing.stream().<Doc>map(CommentDocs::renderComment));
        return Doc.concatOf(Stream.of(Stream.<Doc>of(new Doc.Text("{")), all.map(Doc::indentedLine), Doc.closeBrace()));
    }

    Stream<Doc> blockParts(final String header, final List<Doc> stmts) {
        return blockParts(new Doc.Text(header), stmts);
    }

    Stream<Doc> blockParts(final Doc header, final List<Doc> stmts) {
        return blockParts(header, stmts.stream());
    }

    Stream<Doc> blockParts(final Doc header, final Stream<Doc> stmts) {
        return Stream.concat(Stream.of(header, new Doc.Text(" {")), stmts.map(Doc::indentedLine));
    }

    Doc stripTrailingSemicolonDoc(final Doc doc) {
        if (doc instanceof Doc.Text(var value) && value.endsWith(";")) {
            return new Doc.Text(value.substring(0, value.length() - 1));
        }
        if (doc instanceof Doc.Concat(var parts) && !parts.isEmpty()) {
            final var last = parts.getLast();
            final var stripped = stripTrailingSemicolonDoc(last);
            if (!stripped.equals(last)) {
                final var newParts = new ArrayList<>(parts);
                newParts.set(newParts.size() - 1, stripped);
                return new Doc.Concat(newParts);
            }
        }
        return doc;
    }
}
