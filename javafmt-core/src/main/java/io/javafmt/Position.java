package io.javafmt;

/**
 * A location in a source file, or the sentinel {@link #UNKNOWN} when a diagnostic has no
 * specific location to report (e.g. an error with no source position at all).
 */
public sealed interface Position permits Position.At, Position.Unknown {

    /** The sentinel value for "no known location". */
    Position UNKNOWN = Unknown.INSTANCE;

    /** {@code "line:column"}, or the empty string for {@link #UNKNOWN}. */
    default String display() {
        return switch (this) {
            case At(var line, var col, var ignored) -> line + ":" + col;
            case Unknown() -> "";
        };
    }

    /** A known location: 1-based {@code line} and {@code column}, and a 0-based char {@code offset}. */
    record At(int line, int column, int offset) implements Position {}

    /** No known location. Use {@link Position#UNKNOWN} rather than constructing this directly. */
    record Unknown() implements Position {

        static final Unknown INSTANCE = new Unknown();
    }
}
