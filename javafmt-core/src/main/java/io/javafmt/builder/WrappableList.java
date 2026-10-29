package io.javafmt.builder;

import io.javafmt.doc.Doc;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * A bracketed, separator-joined list that breaks one element per line at the continuation indent
 * when it does not fit, and stays on one line when it does.
 *
 * <p>The shape method parameter lists have always used, factored out because seven other
 * constructs were built as a flat {@code Concat} with no break point anywhere in them. A list with
 * no break point cannot obey the line width at all: it prints at whatever width it happens to be,
 * and {@code java/lang/FdLibm.java} came out with a 711-character line.
 *
 * <p>The closing bracket hugs the last element rather than taking a line of its own, matching what
 * parameter and argument lists already produce.
 */
@UtilityClass
final class WrappableList {

    Doc of(final String open, final String separator, final String close, final Stream<Doc> elements) {
        return new Doc.Group(ungroupedOf(open, separator, close, elements));
    }

    Doc ungroupedOf(final String open, final String separator, final String close, final Stream<Doc> elements) {
        return build(open, separator, close, elements, Stream.empty());
    }

    /**
     * {@link #of} with the closing bracket on a line of its own at the enclosing indent when the
     * list breaks. For a braced initializer, hugging the last element writes {@code 12,};} — the
     * brace that opened a block-shaped thing should close like one.
     */
    Doc blockOf(final String open, final String separator, final String close, final Stream<Doc> elements) {
        return new Doc.Group(build(open, separator, close, elements, Stream.of(Doc.SOFT_LINE)));
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

    private Doc build(final String open, final String separator, final String close, final Stream<Doc> elements, final Stream<Doc> beforeClose) {
        final var interior = new Doc.Concat(
                Stream.concat(Stream.of(Doc.SOFT_LINE), Doc.intersperse(List.of(new Doc.Text(separator), Doc.LINE), elements)));
        return new Doc.Concat(
                Stream.concat(
                        Stream.<Doc>of(new Doc.Text(open), Doc.continuationIndent(interior)),
                        Stream.concat(beforeClose, Stream.<Doc>of(new Doc.Text(close)))));
    }
}
