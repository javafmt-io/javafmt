package io.github.jschneidereit.grind.ir;

import io.github.jschneidereit.grind.parser.CommentToken;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

final class CommentDocs {

    static Doc renderComment(final CommentToken comment) {
        final var lines = Arrays.stream(comment.text().split("\n", -1))
            .map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
            .toList();
        final var strip = comment.startColumn();
        return new Doc.Concat(java.util.stream.IntStream.range(0, lines.size())
            .<Doc>mapToObj(i -> new Doc.Text(i == 0 ? lines.get(i) : stripLeading(lines.get(i), strip)))
            .flatMap(t -> Stream.<Doc>of(new Doc.HardLine(), t))
            .skip(1));
    }

    private static String stripLeading(final String line, final int max) {
        var n = 0;
        while (n < max && n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return line.substring(n);
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
