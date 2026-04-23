package io.github.jschneidereit.grind.parser;

import java.util.Objects;

public record CommentToken(int start, int end, String text, int startColumn) {

    public CommentToken {
        Objects.requireNonNull(text, "text");
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid range: [" + start + ", " + end + ")");
        }
        if (startColumn < 0) {
            throw new IllegalArgumentException("invalid startColumn: " + startColumn);
        }
    }
}
