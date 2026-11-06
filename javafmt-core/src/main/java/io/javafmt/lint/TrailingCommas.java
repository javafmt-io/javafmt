package io.javafmt.lint;

import io.javafmt.parser.CommentScanner;
import lombok.experimental.UtilityClass;

import java.util.stream.IntStream;

@UtilityClass
final class TrailingCommas {

    LintEdit insertAfter(final String source, final int lastEnd) {
        return LintEdit.insert(pastSameLineBlockComments(source, lastEnd), ",");
    }

    private int pastSameLineBlockComments(final String source, final int from) {
        final var start = IntStream.range(from, source.length())
                .filter(i -> source.charAt(i) != ' ' && source.charAt(i) != '\t')
                .findFirst()
                .orElse(from);
        final var end = CommentScanner.skipComment(source, start);
        if (end < 0 || source.charAt(start + 1) != '*') {
            return from;
        }
        return pastSameLineBlockComments(source, end);
    }
}
