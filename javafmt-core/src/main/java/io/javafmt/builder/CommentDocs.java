package io.javafmt.builder;

import io.javafmt.parser.CommentToken;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import io.javafmt.doc.Doc;

final class CommentDocs {

    private static final int TAB_WIDTH = 4;

    static Doc renderComment(final CommentToken comment) {
        final var lines = Arrays.stream(comment.text().split("\n", -1))
            .map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
            .toList();
        if (lines.size() == 1) {
            return new Doc.Text(lines.get(0));
        }
        final var strip = computeStrip(lines, comment.startColumn());
        return new Doc.Concat(Stream.<Doc>concat(
            Stream.of(new Doc.Text(lines.get(0))),
            IntStream.range(1, lines.size()).boxed()
                .flatMap(i -> Stream.<Doc>of(new Doc.HardLine(), new Doc.Text(stripCols(lines.get(i), strip))))));
    }

    private static int computeStrip(final List<String> lines, final int openerCol) {
        return Math.min(openerCol, lines.stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .mapToInt(CommentDocs::leadingCols)
            .min()
            .orElse(openerCol));
    }

    private static int leadingCols(final String line) {
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

    private static String stripCols(final String line, final int strip) {
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

    static Doc prepend(final List<CommentToken> comments, final Doc doc) {
        if (comments.isEmpty()) {
            return doc;
        }
        return new Doc.Concat(Stream.concat(
            comments.stream().flatMap(c -> Stream.<Doc>of(renderComment(c), new Doc.HardLine())),
            Stream.of(doc)));
    }

    static Doc appendTrailing(final Doc doc, final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return doc;
        }
        return new Doc.Concat(Stream.concat(
            Stream.of(doc),
            comments.stream().flatMap(c -> Stream.<Doc>of(new Doc.Text(" "), renderComment(c)))));
    }

    static Stream<Doc> fileHeaderStream(final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return Stream.empty();
        }
        return comments.stream().flatMap(c -> Stream.<Doc>of(renderComment(c), new Doc.HardLine()));
    }

    static Stream<Doc> fileFooterStream(final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return Stream.empty();
        }
        return comments.stream().flatMap(c -> Stream.<Doc>of(new Doc.HardLine(), renderComment(c)));
    }

    private CommentDocs() {}
}
