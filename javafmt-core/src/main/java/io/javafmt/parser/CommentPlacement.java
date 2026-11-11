package io.javafmt.parser;

import io.javafmt.Diagnostic;
import io.javafmt.Position;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;

@UtilityClass
public final class CommentPlacement {

    public final String MESSAGE = "a comment goes on its own line or at the end of a line, not between code";

    public Optional<ParseException> commentsBetweenCode(final String source) {
        final var comments = CommentScanner.scan(source);
        final var inComment = new boolean[source.length()];
        comments.forEach(comment -> Arrays.fill(inComment, comment.start(), comment.end(), true));
        final var errors = comments.stream()
                .filter(comment -> codeBefore(source, inComment, comment.start()) && codeAfter(source, inComment, comment.end()))
                .<Diagnostic>map(comment -> new Diagnostic.LintError(MESSAGE, positionOf(source, comment.start())))
                .toList();
        return errors.isEmpty() ? Optional.empty() : Optional.of(new ParseException(MESSAGE, errors));
    }

    private boolean codeBefore(final String source, final boolean[] inComment, final int start) {
        return IntStream.iterate(start - 1, i -> i >= 0 && source.charAt(i) != '\n', i -> i - 1)
                .anyMatch(i -> !inComment[i] && !Character.isWhitespace(source.charAt(i)));
    }

    private boolean codeAfter(final String source, final boolean[] inComment, final int end) {
        return IntStream.iterate(end, i -> i < source.length() && source.charAt(i) != '\n', i -> i + 1)
                .anyMatch(i -> !inComment[i] && !Character.isWhitespace(source.charAt(i)));
    }

    private Position positionOf(final String source, final int offset) {
        final var line = (int) source.substring(0, offset)
                .chars()
                .filter(c -> c == '\n')
                .count()
                + 1;
        return new Position.At(line, offset - (source.lastIndexOf('\n', offset - 1) + 1) + 1, offset);
    }
}
