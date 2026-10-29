package io.javafmt.builder;

import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentToken;
import java.util.Arrays;
import java.util.List;
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
        final var strip = computeStrip(lines, comment.startColumn());
        return new Doc.Concat(
                Stream.concat(
                        Stream.of(new Doc.Text(lines.getFirst())),
                        IntStream.range(1, lines.size())
                                .boxed()
                                .flatMap(i -> Stream.<Doc>of(Doc.HARD_LINE, new Doc.Text(stripCols(lines.get(i), strip))))));
    }

    private int computeStrip(final List<String> lines, final int openerCol) {
        return Math.min(openerCol, lines.stream().skip(1).filter(line -> !line.isBlank()).mapToInt(CommentDocs::leadingCols).min().orElse(openerCol));
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

    private String stripCols(final String line, final int strip) {
        var col = 0;
        var i = 0;
        while (i < line.length()) {
            final var c = line.charAt(i);
            if (c == ' ') {
                col++;
                i++;
            } else if (c == '\t') {
                col = (col / TAB_WIDTH + 1) * TAB_WIDTH;
                i++;
            } else {
                break;
            }
        }
        final var remaining = Math.max(0, col - strip);
        return " ".repeat(remaining) + line.substring(i);
    }

    Doc prepend(final List<CommentToken> comments, final Doc doc) {
        return prepend(comments, List.of(), doc);
    }

    /**
     * The leading comments in front of {@code doc}: {@code ownLine} ones each take a line, while
     * {@code inline} ones — the ones the author wrote on the node's own line, as in
     * {@code f(/* count *&#47; 3)} — stay on it. Giving an inline comment a line of its own puts a
     * forced break inside the enclosing list, which breaks the whole list one element per line
     * however well it would have fitted.
     */
    Doc prepend(final List<CommentToken> ownLine, final List<CommentToken> inline, final Doc doc) {
        if (ownLine.isEmpty() && inline.isEmpty()) {
            return doc;
        }
        return Doc.concatOf(
                ownLine.stream().flatMap(c -> Stream.of(renderComment(c), Doc.HARD_LINE)),
                inline.stream().flatMap(c -> Stream.of(renderComment(c), new Doc.Text(" "))),
                Stream.of(doc));
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
