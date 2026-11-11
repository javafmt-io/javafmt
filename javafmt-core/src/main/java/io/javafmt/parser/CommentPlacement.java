package io.javafmt.parser;

import io.javafmt.Diagnostic;
import io.javafmt.Position;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
public final class CommentPlacement {

    public final String BETWEEN_CODE = "a comment goes on its own line or at the end of a line, not between code";

    public final String UNSUPPORTED = "javafmt cannot keep a comment here; move it above the statement or to the end of a line";

    public Optional<ParseException> misplacedComments(final ParsedUnit unit) {
        final var source = unit.source();
        final var comments = CommentScanner.scan(source);
        final var inComment = new boolean[source.length()];
        comments.forEach(comment -> Arrays.fill(inComment, comment.start(), comment.end(), true));
        final var betweenCode = comments.stream()
                .filter(comment -> codeBefore(source, inComment, comment.start()) && codeAfter(source, inComment, comment.end()))
                .toList();
        final var errors = Stream.concat(
                betweenCode.stream()
                        .map(comment -> new Misplaced(comment, BETWEEN_CODE)),
                unit.loose().stream()
                        .filter(comment -> !betweenCode.contains(comment))
                        .map(comment -> new Misplaced(comment, UNSUPPORTED)))
                .sorted(Comparator.comparingInt(misplaced -> misplaced.comment().start()))
                .<Diagnostic>map(misplaced -> new Diagnostic.LintError(
                        misplaced.message(),
                        positionOf(source, misplaced.comment().start())))
                .toList();
        return errors.isEmpty() ? Optional.empty() : Optional.of(new ParseException(BETWEEN_CODE, errors));
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

    private record Misplaced(CommentToken comment, String message) {}
}
