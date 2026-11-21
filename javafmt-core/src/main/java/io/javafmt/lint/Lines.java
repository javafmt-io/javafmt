package io.javafmt.lint;

import lombok.experimental.UtilityClass;

@UtilityClass
final class Lines {

    int start(final String src, final int pos) {
        return src.lastIndexOf('\n', pos - 1) + 1;
    }

    int end(final String src, final int pos) {
        final var newline = src.indexOf('\n', pos);
        return newline < 0 ? src.length() : newline + 1;
    }

    String indentAt(final String src, final int pos) {
        final var lineStart = start(src, pos);
        var end = lineStart;
        while (end < pos && (src.charAt(end) == ' ' || src.charAt(end) == '\t')) {
            end++;
        }
        return src.substring(lineStart, end);
    }

    int column(final String src, final int pos) {
        return pos - start(src, pos);
    }
}
