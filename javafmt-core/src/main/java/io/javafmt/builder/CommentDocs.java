package io.javafmt.builder;

import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
     * forced break inside the enclosing list, which breaks it however well it would have fitted.
     */
    Doc prepend(final List<CommentToken> ownLine, final List<CommentToken> inline, final Doc doc) {
        if (ownLine.isEmpty() && inline.isEmpty()) {
            return doc;
        }
        return Doc.concatOf(
                Stream.of(
                        ownLine.stream().flatMap(c -> Stream.of(renderComment(c), Doc.HARD_LINE)),
                        inline.stream().flatMap(c -> Stream.of(renderComment(c), new Doc.Text(" "))), Stream.of(doc)));
    }

    Doc appendTrailing(final Doc doc, final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return doc;
        }
        return new Doc.Concat(Stream.concat(Stream.of(doc), comments.stream().flatMap(c -> Stream.of(new Doc.Text(" "), renderComment(c)))));
    }

    Doc withSeparator(final Tree tree, final UnaryOperator<Doc> adjust, final String separator, final Recursor recursor) {
        if (separator.isEmpty()) {
            return recursor.scanBeforeComments(tree, adjust);
        }
        if (hasLineComment(recursor.trailing(tree))) {
            return recursor.scanBeforeComments(tree, d -> Doc.concat(adjust.apply(d), new Doc.Text(separator)));
        }
        return Doc.concat(recursor.scanBeforeComments(tree, adjust), new Doc.Text(separator));
    }

    private Doc attachedWithComma(final Tree item, final Doc doc, final boolean comma, final LeadingCommentAttacher attacher) {
        if (!comma) {
            return attacher.attach(item, doc);
        }
        if (hasLineComment(attacher.trailing(item))) {
            return attacher.attach(item, Doc.concat(doc, new Doc.Text(",")));
        }
        return Doc.concat(attacher.attach(item, doc), new Doc.Text(","));
    }

    List<CommentToken> inside(final Tree node, final LeadingCommentAttacher attacher) {
        final var pastTheSeparator = attacher.pastTheSeparatorBefore(node);
        return attacher.interior(node).stream().filter(c -> !pastTheSeparator.contains(c)).toList();
    }

    boolean hasLineComment(final List<CommentToken> comments) {
        return comments.stream().anyMatch(c -> c.text().startsWith("//"));
    }

    Doc closeAfter(final Doc doc, final List<CommentToken> ownLine, final String closer) {
        final var withComments = ownLine.isEmpty()
                ? doc
                : Doc.concatOf(Stream.of(Stream.of(doc), ownLine.stream().flatMap(c -> Stream.of(Doc.HARD_LINE, renderComment(c)))));
        return Doc.endsWithLineComment(withComments)
                ? Doc.concat(withComments, Doc.HARD_LINE, new Doc.Text(closer))
                : Doc.concat(withComments, new Doc.Text(closer));
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

    <T extends Tree> Stream<Doc> commaList(final List<? extends T> items,
                                           final LeadingCommentAttacher attacher,
                                           final Function<? super T, Doc> render) {
        final var rendered = IntStream.range(0, items.size())
                .mapToObj(i -> attachedWithComma(items.get(i), render.apply(items.get(i)), i + 1 < items.size(), attacher))
                .toList();
        return IntStream.range(0, items.size())
                .boxed()
                .flatMap(
                        i -> Stream.concat(
                                i == 0 ? Stream.empty() : Stream.of(afterComma(rendered.get(i - 1), attacher.pastTheSeparatorBefore(items.get(i)))),
                                Stream.of(rendered.get(i))));
    }

    Doc commaFill(final List<? extends Tree> items, final List<Doc> renderedWithCommas, final LeadingCommentAttacher attacher) {
        return new Doc.Fill(
                IntStream.range(0, items.size())
                        .boxed()
                        .flatMap(
                                i -> Stream.concat(
                                        i == 0 ? Stream.empty() : Stream.of(afterComma(renderedWithCommas.get(i - 1), attacher.pastTheSeparatorBefore(items.get(i)))),
                                        Stream.of(renderedWithCommas.get(i))))
                        .toList());
    }

    private Doc afterComma(final Doc previous, final List<CommentToken> afterSeparator) {
        if (afterSeparator.isEmpty() && Doc.endsWithLineComment(previous)) {
            return Doc.HARD_LINE;
        }
        return new Doc.Concat(listSeparator("", afterSeparator));
    }

    boolean commentBeforeFirstBreak(final Doc doc) {
        final var work = new ArrayDeque<Doc>();
        work.push(doc);
        while (!work.isEmpty()) {
            switch (work.pop()) {
                case Doc.Text(var text) -> {
                    if (text.startsWith("//") || text.startsWith("/*")) {
                        return true;
                    }
                }
                case Doc.BlockComment _ -> {
                    return true;
                }
                case Doc.HardLine _, Doc.LineStart _ -> {
                    return false;
                }
                case Doc.Verbatim _, Doc.Line _, Doc.SoftLine _ -> {}
                case Doc.Indent(var inner) -> work.push(inner);
                case Doc.Align(var inner) -> work.push(inner);
                case Doc.Group(var inner, var _) -> work.push(inner);
                case Doc.IfBreak(var _, var flatContents) -> work.push(flatContents);
                case Doc.Choice(var preferred, var _, var _) -> work.push(preferred);
                case Doc.Concat(var parts) -> parts.reversed().forEach(work::push);
                case Doc.Fill(var parts) -> parts.reversed().forEach(work::push);
            }
        }
        return false;
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
