package io.javafmt;

/**
 * A location in a source file, or {@link Unknown} when a diagnostic has no specific location to
 * report (e.g. an error with no source position at all).
 */
public sealed interface Position permits Position.At, Position.Unknown {

    /** {@code "line:column"}, or the empty string for {@link Unknown}. */
    default String display() {
        return switch (this) {
            case At(var line, var col, var ignored) -> line + ":" + col;
            case Unknown() -> "";
        };
    }

    /** A known location: 1-based {@code line} and {@code column}, and a 0-based char {@code offset}. */
    record At(int line, int column, int offset) implements Position {}

    /** No known location. */
    record Unknown() implements Position {}
}
