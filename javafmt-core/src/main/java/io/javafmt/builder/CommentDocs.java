package io.javafmt.builder;

import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
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
                Stream.concat(
                        Stream.of(lines.getFirst()),
                        lines.stream()
                                .skip(1)
                                .map(CommentDocs::expandLeadingWhitespace))
                        .toList(),
                comment.startColumn());
    }

    String expandLeadingWhitespace(final String line) {
        final var indentEnd = (int) line.chars()
                .takeWhile(c -> c == ' ' || c == '\t')
                .count();
        return " ".repeat(expandedWidth(line.substring(0, indentEnd))) + line.substring(indentEnd);
    }

    int expandedWidth(final String text) {
        return text.chars().reduce(0, (column, c) -> c == '\t' ? (column / TAB_WIDTH + 1) * TAB_WIDTH : column + 1);
    }

    Doc prepend(final List<CommentToken> comments, final Doc doc) {
        if (comments.isEmpty()) {
            return doc;
        }
        return Doc.concatOf(
                Stream.of(
                        comments.stream()
                                .flatMap(c -> Stream.of(renderComment(c), Doc.HARD_LINE)),
                        Stream.of(doc)));
    }

    Doc appendTrailing(final Doc doc, final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return doc;
        }
        return new Doc.Concat(
                Stream.concat(Stream.of(doc), comments.stream()
                        .flatMap(c -> Stream.of(new Doc.Text(" "), renderComment(c)))));
    }

    Doc withSeparator(final Tree tree, final UnaryOperator<Doc> adjust, final String separator, final Recursor recursor) {
        if (separator.isEmpty()) {
            return recursor.scanBeforeComments(tree, adjust);
        }
        if (endsTheLine(recursor.trailing(tree))) {
            return recursor.scanBeforeComments(tree, d -> Doc.concat(adjust.apply(d), new Doc.Text(separator)));
        }
        return Doc.concat(recursor.scanBeforeComments(tree, adjust), new Doc.Text(separator));
    }

    Doc attachedWithSeparator(final Tree item, final Doc doc, final String separator, final LeadingCommentAttacher attacher) {
        return separator.isEmpty() ? attacher.attach(item, doc) : attachedWithSeparator(item, doc, new Doc.Text(separator), attacher);
    }

    private Doc attachedWithSeparator(final Tree item, final Doc doc, final Doc separator, final LeadingCommentAttacher attacher) {
        if (endsTheLine(attacher.trailing(item))) {
            return attacher.attach(item, Doc.concat(doc, separator));
        }
        return Doc.concat(attacher.attach(item, doc), separator);
    }

    boolean endsTheLine(final List<CommentToken> comments) {
        return !comments.isEmpty();
    }

    Doc closeAfter(final Doc doc, final List<CommentToken> ownLine, final String closer) {
        final var withComments = ownLine.isEmpty() ? doc : Doc.concatOf(Stream.of(Stream.of(doc), eachOnItsOwnLine(ownLine)));
        return Doc.endsWithLineComment(withComments)
                ? Doc.concat(withComments, Doc.HARD_LINE, new Doc.Text(closer))
                : Doc.concat(withComments, new Doc.Text(closer));
    }

    <T extends Tree> Stream<Doc> commaList(final List<? extends T> items,
                                           final LeadingCommentAttacher attacher,
                                           final Function<? super T, Doc> render) {
        return commaList(items, attacher, render, Optional.empty());
    }

    <T extends Tree> Stream<Doc> commaList(final List<? extends T> items,
                                           final LeadingCommentAttacher attacher,
                                           final Function<? super T, Doc> render,
                                           final Optional<Doc> lastSeparator) {
        final var rendered = IntStream.range(0, items.size())
                .mapToObj(i -> i + 1 < items.size()
                        ? attachedWithSeparator(items.get(i), render.apply(items.get(i)), new Doc.Text(","), attacher)
                        : lastSeparator.map(separator -> attachedWithSeparator(items.get(i), render.apply(items.get(i)), separator, attacher))
                                .orElseGet(() -> attacher.attach(items.get(i), render.apply(items.get(i)))))
                .toList();
        return interleaved(rendered, i -> attacher.pastTheSeparatorBefore(items.get(i)));
    }

    Doc commaSeparated(final List<Doc> renderedWithCommas, final List<List<CommentToken>> pastTheSeparator) {
        return new Doc.Concat(
                interleaved(renderedWithCommas, pastTheSeparator::get).toList());
    }

    private Stream<Doc> interleaved(final List<Doc> renderedWithCommas, final IntFunction<List<CommentToken>> pastTheSeparator) {
        return IntStream.range(0, renderedWithCommas.size())
                .boxed()
                .flatMap(i -> Stream.concat(
                        i == 0 ? Stream.empty() : Stream.of(afterComma(renderedWithCommas.get(i - 1), pastTheSeparator.apply(i))),
                        Stream.of(renderedWithCommas.get(i))));
    }

    Doc afterComma(final Doc previous, final List<CommentToken> afterSeparator) {
        if (afterSeparator.isEmpty()) {
            return Doc.endsWithLineComment(previous) ? Doc.HARD_LINE : Doc.LINE;
        }
        return Doc.concatOf(
                Stream.of(
                        afterSeparator.stream()
                                .flatMap(c -> Stream.of(new Doc.Text(" "), renderComment(c))),
                        Stream.of(Doc.HARD_LINE)));
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
                case Doc.Choice(var preferred, var _, var _, var _) -> work.push(preferred);
                case Doc.Concat(var parts) -> parts.reversed().forEach(work::push);
            }
        }
        return false;
    }

    Stream<Doc> fileHeaderStream(final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return Stream.empty();
        }
        return comments.stream()
                .flatMap(c -> Stream.of(renderComment(c), Doc.HARD_LINE));
    }

    Stream<Doc> eachOnItsOwnLine(final List<CommentToken> comments) {
        return comments.stream()
                .flatMap(c -> Stream.of(Doc.HARD_LINE, renderComment(c)));
    }
}
