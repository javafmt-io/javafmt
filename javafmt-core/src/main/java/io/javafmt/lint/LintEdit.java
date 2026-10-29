package io.javafmt.lint;

import java.util.Objects;

/**
 * A text edit produced by a {@link LintRule}. Half-open range {@code [start, end)} into
 * the source string is replaced with {@link #replacement}; pure insertions use
 * {@code start == end}.
 */
public record LintEdit(int start, int end, String replacement) {

    public LintEdit {
        Objects.requireNonNull(replacement, "replacement");
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid range: start=" + start + ", end=" + end);
        }
    }

    public static LintEdit insert(final int at, final String text) {
        return new LintEdit(at, at, text);
    }
}
