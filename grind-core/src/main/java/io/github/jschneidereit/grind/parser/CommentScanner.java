package io.github.jschneidereit.grind.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CommentScanner {

    public static List<CommentToken> scan(final String source) {
        Objects.requireNonNull(source, "source");
        final var comments = new ArrayList<CommentToken>();
        final var n = source.length();
        var i = 0;
        while (i < n) {
            final var c = source.charAt(i);
            if (c == '/' && i + 1 < n) {
                final var next = source.charAt(i + 1);
                if (next == '/') {
                    final var start = i;
                    final var col = columnOf(source, start);
                    i += 2;
                    while (i < n && source.charAt(i) != '\n' && source.charAt(i) != '\r') {
                        i++;
                    }
                    comments.add(new CommentToken(start, i, source.substring(start, i), col));
                    continue;
                }
                if (next == '*') {
                    final var start = i;
                    final var col = columnOf(source, start);
                    i += 2;
                    while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                        i++;
                    }
                    final var end = Math.min(i + 2, n);
                    comments.add(new CommentToken(start, end, source.substring(start, end), col));
                    i = end;
                    continue;
                }
            }
            if (c == '"') {
                if (i + 2 < n && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                    i = skipTextBlock(source, i, n);
                    continue;
                }
                i = skipStringLiteral(source, i, n);
                continue;
            }
            if (c == '\'') {
                i = skipCharLiteral(source, i, n);
                continue;
            }
            i++;
        }
        return List.copyOf(comments);
    }

    private static int columnOf(final String source, final int pos) {
        var col = 0;
        var i = pos - 1;
        while (i >= 0 && source.charAt(i) != '\n' && source.charAt(i) != '\r') {
            i--;
            col++;
        }
        return col;
    }

    private static int skipStringLiteral(final String source, final int open, final int n) {
        var i = open + 1;
        while (i < n) {
            final var c = source.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == '"' || c == '\n' || c == '\r') {
                return c == '"' ? i + 1 : i;
            }
            i++;
        }
        return i;
    }

    private static int skipCharLiteral(final String source, final int open, final int n) {
        var i = open + 1;
        while (i < n) {
            final var c = source.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == '\'' || c == '\n' || c == '\r') {
                return c == '\'' ? i + 1 : i;
            }
            i++;
        }
        return i;
    }

    private static int skipTextBlock(final String source, final int open, final int n) {
        var i = open + 3;
        while (i + 2 < n) {
            final var c = source.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == '"' && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                return i + 3;
            }
            i++;
        }
        return n;
    }

    private CommentScanner() {}
}
