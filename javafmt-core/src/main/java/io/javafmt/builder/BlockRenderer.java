package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
final class BlockRenderer {

    List<Doc> blockStatements(final StatementTree statement, final Recursor recursor) {
        if (statement instanceof BlockTree block) {
            final var statementDocs = statementDocs(block.getStatements(), recursor);
            final var tailDocs = recursor.trailingInside(block).stream()
                    .map(CommentDocs::renderComment);
            return Stream.concat(statementDocs, tailDocs).toList();
        }
        recursor.emitWarning("added braces around single-statement body", statement);
        return Optional.ofNullable(recursor.scan(statement))
                .map(List::of)
                .orElse(List.of());
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
                .mapToObj(i -> Optional.ofNullable(recursor.scan(statements.get(i)))
                        .map(doc -> i > 0 && recursor.blankLineBetween(statements.get(i - 1), statements.get(i))
                                ? Doc.concat(Doc.HARD_LINE, doc)
                                : doc))
                .flatMap(Optional::stream);
    }

    Doc braced(final Doc header, final StatementTree body, final Recursor recursor) {
        final var statements = blockStatements(body, recursor);
        if (statements.isEmpty() && braceLineOf(body, recursor).isEmpty()) {
            return Doc.concat(header, new Doc.Text(" {}"));
        }
        return Doc.concatOf(Stream.of(openedWith(header, body, statements, recursor), Doc.closeBrace()));
    }

    Stream<Doc> openedWith(final Doc header, final StatementTree body, final List<Doc> statements, final Recursor recursor) {
        return Stream.concat(opened(header, body, recursor), statements.stream()
                .map(Doc::indentedLine));
    }

    Stream<Doc> opened(final Doc header, final StatementTree body, final Recursor recursor) {
        return Stream.concat(Stream.of(header, new Doc.Text(" {")), braceLineAfter(body, recursor));
    }

    boolean writtenOnOneLine(final Tree owner, final BlockTree body, final Recursor recursor) {
        if (recursor.sourceOf(body).lines().count() != 1) {
            return false;
        }
        final var source = recursor.sourceOf(owner);
        return CommentScanner.withoutComments(source).equals(source);
    }

    Stream<Doc> braceLineAfter(final StatementTree body, final Recursor recursor) {
        return braceLineComments(braceLineOf(body, recursor));
    }

    Stream<Doc> braceLineComments(final List<CommentToken> braceLine) {
        return Stream.concat(besideTheBrace(braceLine), belowTheBrace(braceLine));
    }

    Stream<Doc> besideTheBrace(final List<CommentToken> braceLine) {
        return braceLine.subList(0, besideCount(braceLine)).stream()
                .flatMap(c -> Stream.of(new Doc.Text(" "), CommentDocs.renderComment(c)));
    }

    Stream<Doc> belowTheBrace(final List<CommentToken> braceLine) {
        return braceLine.subList(besideCount(braceLine), braceLine.size())
                .stream()
                .map(c -> Doc.indentedLine(CommentDocs.renderComment(c)));
    }

    private int besideCount(final List<CommentToken> braceLine) {
        return Math.min(1, braceLine.size());
    }

    private List<CommentToken> braceLineOf(final StatementTree body, final Recursor recursor) {
        return body instanceof BlockTree block ? recursor.braceLine(block) : List.of();
    }

    /**
     * A block, with {@code trailing} — the comments nothing inside it follows — written after the
     * statements rather than before them. An empty block has only those comments so the order does
     * not show there, but a static initializer ending in a block of notes had them hoisted above
     * its first statement, which is not where the author left them.
     */
    Doc buildBlock(final Doc opening, final List<CommentToken> braceLine, final List<Doc> statements, final List<CommentToken> trailing) {
        final var all = Stream.concat(
                statements.stream(),
                trailing.stream()
                        .map(CommentDocs::renderComment))
                .toList();
        if (all.isEmpty() && braceLine.isEmpty()) {
            return Doc.concat(opening, new Doc.Text("}"));
        }
        return Doc.concatOf(
                Stream.of(
                        Stream.of(opening),
                        braceLineComments(braceLine),
                        all.stream()
                                .map(Doc::indentedLine),
                        Doc.closeBrace()));
    }

    Doc stripTrailingSemicolonDoc(final Doc doc) {
        if (doc instanceof Doc.Text(var value) && value.endsWith(";")) {
            return new Doc.Text(value.substring(0, value.length() - 1));
        }
        if (doc instanceof Doc.Choice(var preferred, var fallback, var fit, var chainWidth)) {
            return new Doc.Choice(stripTrailingSemicolonDoc(preferred), stripTrailingSemicolonDoc(fallback), fit, chainWidth);
        }
        if (doc instanceof Doc.Indent(var contents)) {
            return new Doc.Indent(stripTrailingSemicolonDoc(contents));
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
