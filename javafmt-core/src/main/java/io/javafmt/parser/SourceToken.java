package io.javafmt.parser;

import java.util.Objects;

/** A single lexical token produced by {@link TokenScanner}, with its span in the source. */
public record SourceToken(int start, int end, String text) {

    public SourceToken {
        Objects.requireNonNull(text, "text");
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid range: [" + start + ", " + end + ")");
        }
    }
}
