package io.javafmt;

/**
 * A location in a source file, or {@link Unknown} when a diagnostic has no specific location to
 * report (e.g. an error with no source position at all).
 */
public sealed interface Position permits Position.At, Position.Unknown {

    int line();

    int column();

    /** {@code "line:column"}, or the empty string for {@link Unknown}. */
    default String display() {
        return switch (this) {
            case At(var line, var col, var _) -> line + ":" + col;
            case Unknown() -> "";
        };
    }

    static Position at(final String source, final int offset) {
        final var line = (int) source.substring(0, offset)
                .chars()
                .filter(c -> c == '\n')
                .count()
                + 1;
        return new At(line, offset - (source.lastIndexOf('\n', offset - 1) + 1) + 1, offset);
    }

    /** A known location: 1-based {@code line} and {@code column}, and a 0-based char {@code offset}. */
    record At(int line, int column, int offset) implements Position {}

    /** No known location. */
    record Unknown() implements Position {

        @Override
        public int line() {
            return 0;
        }

        @Override
        public int column() {
            return 0;
        }
    }
}
