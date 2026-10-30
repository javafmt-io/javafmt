package io.javafmt.lint;

import io.javafmt.parser.CommentScanner;
import lombok.experimental.UtilityClass;

/**
 * Line-width arithmetic for the two rules that add a trailing comma.
 *
 * <p>{@code EnumTrailingComma} adds its comma only to a constant list that is going to print one
 * per line, which means a lint rule has to answer a question the printer normally owns: will this
 * fit? It can be answered here because an enum declaration always starts a line of its own, so its
 * column does not move when javafmt reformats the file and the same source gives the same answer on
 * every run. The same trick does not work for an array initializer, which can sit anywhere on a
 * line, so whether it prints on one line is a printer decision a lint rule cannot make.
 */
@UtilityClass
final class SourceLayout {

    final int LINE_WIDTH = 150;

    /** The source with its comments removed and every run of whitespace collapsed to one space. */
    String flatten(final String source) {
        return CommentScanner.withoutComments(source).strip().replaceAll("\\s+", " ");
    }

    /** How many characters precede {@code pos} on its line. */
    int columnOf(final String source, final int pos) {
        return pos - (source.lastIndexOf('\n', pos - 1) + 1);
    }

    /** Whether {@code width} characters starting at column {@code column} stay inside the line. */
    boolean fits(final int column, final int width) {
        return column + width <= LINE_WIDTH;
    }
}
