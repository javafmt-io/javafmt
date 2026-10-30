package io.javafmt.builder;

import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import io.javafmt.parser.CommentToken;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class CommentDocs {

    private final int TAB_WIDTH = 4;

    Doc renderComment(final CommentToken comment) {
        final var lines = Arrays.stream(comment.text().split("\n", -1))
                .map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
                .toList();
        if (lines.size() == 1) {
            return new Doc.Text(lines.getFirst());
        }
        return new Doc.BlockComment(
                Stream.concat(Stream.of(lines.getFirst()), lines.stream().skip(1).map(CommentDocs::expandLeadingWhitespace)).toList(),
                comment.startColumn());
    }

    private int leadingCols(final String line) {
        var col = 0;
        for (var i = 0; i < line.length(); i++) {
            final var c = line.charAt(i);
            if (c == ' ') {
                col++;
            } else if (c == '\t') {
                col = (col / TAB_WIDTH + 1) * TAB_WIDTH;
            } else {
                break;
            }
        }
        return col;
    }

    private String expandLeadingWhitespace(final String line) {
        final var indentEnd = (int) line.chars().takeWhile(c -> c == ' ' || c == '\t').count();
        return " ".repeat(leadingCols(line)) + line.substring(indentEnd);
    }

    Doc prepend(final List<CommentToken> comments, final Doc doc) {
        return prepend(comments, List.of(), doc);
    }

    /**
     * The leading comments in front of {@code doc}: {@code ownLine} ones each take a line, while
     * {@code inline} ones — the ones the author wrote on the node's own line, as in
     * <code>f(/* count *&#47; 3)</code> — stay on it. Giving an inline comment a line of its own puts a
     * forced break inside the enclosing list, which breaks the whole list one element per line
     * however well it would have fitted.
     */
    Doc prepend(final List<CommentToken> ownLine, final List<CommentToken> inline, final Doc doc) {
        if (ownLine.isEmpty() && inline.isEmpty()) {
            return doc;
        }
        return Doc.concatOf(
                Stream.of(
                        ownLine.stream().flatMap(c -> Stream.of(renderComment(c), Doc.HARD_LINE)),
                        inline.stream().flatMap(c -> Stream.of(renderComment(c), new Doc.Text(" "))),
                        Stream.of(doc)));
    }

    Doc appendTrailing(final Doc doc, final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return doc;
        }
        return new Doc.Concat(Stream.concat(Stream.of(doc), comments.stream().flatMap(c -> Stream.of(new Doc.Text(" "), renderComment(c)))));
    }

    /**
     * The separator that joins a list element to the one before it: the punctuation, then any
     * comment the author left after that punctuation, then the break. The comment comes after the
     * separator because a {@code //} comment emitted before it would swallow the comma, and the
     * break after such a comment is hard for the same reason — the next element cannot share its
     * line.
     */
    List<Doc> listSeparator(final String separator, final List<CommentToken> afterSeparator) {
        if (afterSeparator.isEmpty()) {
            return List.of(new Doc.Text(separator), Doc.LINE);
        }
        return Stream.concat(
                Stream.<Doc>of(new Doc.Text(separator)),
                Stream.concat(afterSeparator.stream().flatMap(c -> Stream.of(new Doc.Text(" "), renderComment(c))), Stream.of(Doc.HARD_LINE)))
                .toList();
    }

    <T extends Tree> Stream<Doc> commaList(
            final List<? extends T> items,
            final LeadingCommentAttacher attacher,
            final Function<? super T, Doc> render) {
        if (items.size() == 1) {
            return Stream.of(render.apply(items.getFirst()));
        }
        return IntStream.range(0, items.size())
                .boxed()
                .flatMap(
                        i -> Stream.concat(
                                i == 0 ? Stream.empty() : listSeparator(",", attacher.interior(items.get(i))).stream(),
                                Stream.of(render.apply(items.get(i)))));
    }

    Stream<Doc> fileHeaderStream(final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return Stream.empty();
        }
        return comments.stream().flatMap(c -> Stream.of(renderComment(c), Doc.HARD_LINE));
    }

    Stream<Doc> fileFooterStream(final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return Stream.empty();
        }
        return comments.stream().flatMap(c -> Stream.of(Doc.HARD_LINE, renderComment(c)));
    }
}
