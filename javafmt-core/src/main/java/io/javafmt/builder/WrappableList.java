package io.javafmt.builder;

import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A bracketed, separator-joined list that breaks one element per line at the continuation indent
 * when it does not fit, and stays on one line when it does. {@link #alignedOf} lines the elements
 * up under the first one instead, falling back to the continuation indent when that is too wide.
 *
 * <p>Factored out because seven constructs were built as a flat {@code Concat} with no break
 * point anywhere in them. A list with no break point cannot obey the line width at all: it prints
 * at whatever width it happens to be, and {@code java/lang/FdLibm.java} came out with a
 * 711-character line.
 *
 * <p>The closing bracket hugs the last element rather than taking a line of its own, matching what
 * parameter and argument lists already produce. In a list whose elements carry their own
 * separators, a last element that ends in a {@code //} comment would swallow the bracket, so there
 * the bracket goes on the next line.
 */
@UtilityClass
final class WrappableList {
    Doc of(final String open, final String separator, final String close, final Stream<Doc> elements) {
        final var interior = new Doc.Concat(
                Stream.concat(Stream.of(Doc.SOFT_LINE), Doc.intersperse(List.of(new Doc.Text(separator), Doc.LINE), elements)));
        return new Doc.Group(Doc.concat(new Doc.Text(open), Doc.continuationIndent(interior), new Doc.Text(close)));
    }

    Doc separatedOf(final String open, final String close, final List<Element> elements) {
        return separated(open, close, elements, Stream.empty());
    }

    /**
     * A list whose elements each carry their own separator, with the closing bracket on a line of
     * its own at the enclosing indent when the list breaks. For a braced initializer, hugging the
     * last element writes {@code 12,};} — the brace that opened a block-shaped thing should close
     * like one. The separator is the element's so that it comes before the element's trailing
     * comment, and the break after an element ending in a {@code //} comment is a hard one.
     */
    Doc blockOf(final String open, final String close, final List<Element> elements) {
        return separated(open, close, elements, Stream.of(Doc.SOFT_LINE));
    }

    private Doc separated(final String open, final String close, final List<Element> elements, final Stream<Doc> beforeClose) {
        final var closing = elements.getLast().endsItsLine() ? Stream.<Doc>of(Doc.HARD_LINE) : beforeClose;
        return new Doc.Group(Doc.concatOf(Stream.of(Stream.of(openedSeparated(open, elements)), closing, Stream.of(new Doc.Text(close)))));
    }

    Doc openedSeparated(final String open, final List<Element> elements) {
        final var breaks = IntStream.range(1, elements.size())
                .mapToObj(i -> Stream.of(elements.get(i - 1).endsItsLine() ? Doc.HARD_LINE : Doc.LINE, elements.get(i).doc()))
                .flatMap(Function.identity());
        final var interior = new Doc.Concat(Stream.concat(Stream.of(Doc.SOFT_LINE, elements.getFirst().doc()), breaks));
        return Doc.concat(new Doc.Text(open), Doc.continuationIndent(interior));
    }

    /**
     * An unbracketed list with the same break behaviour: one element per line at the continuation
     * indent when it does not fit, one line when it does. {@code between} is what joins two
     * elements — put the punctuation before the {@link Doc#LINE} to leave it at the end of a line,
     * as a {@code case} label list wants, or after it to lead the next one, as the {@code |} of a
     * multi-catch does.
     */
    Doc joined(final List<Doc> between, final Stream<Doc> elements) {
        return new Doc.Group(Doc.continuationIndent(new Doc.Concat(Doc.intersperse(between, elements))));
    }

    record Element(Doc doc, boolean endsItsLine) {}

    Doc alignedOf(final String open, final List<Doc> interior, final String close) {
        final var aligned = new Doc.Align(new Doc.Concat(interior));
        final var indented = Doc.continuationIndent(new Doc.Concat(Stream.concat(Stream.of(Doc.SOFT_LINE), interior.stream())));
        final var layout = Doc.flatWidthOrBreak(interior.getFirst()) < 0 ? indented : new Doc.Choice(aligned, indented);
        return Doc.concat(new Doc.Text(open), layout, new Doc.Text(close));
    }
}
