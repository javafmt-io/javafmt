package io.javafmt.lint;

record Span(int start, int end) {

    boolean covers(final int pos) {
        return start <= pos && pos < end;
    }

    boolean overlaps(final Span other) {
        return start < other.end() && other.start() < end;
    }

    int length() {
        return end - start;
    }
}
