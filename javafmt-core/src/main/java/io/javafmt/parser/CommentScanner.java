package io.javafmt.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class CommentScanner {

    public List<CommentToken> scan(final String source) {
        Objects.requireNonNull(source, "source");
        final var comments = new ArrayList<CommentToken>();
        final var n = source.length();
        var i = 0;
        while (i < n) {
            final var c = source.charAt(i);
            final var commentEnd = skipComment(source, i);
            if (commentEnd >= 0) {
                comments.add(new CommentToken(i, commentEnd, source.substring(i, commentEnd), columnOf(source, i)));
                i = commentEnd;
                continue;
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

    /**
     * If a comment starts at {@code pos}, the position immediately after it; otherwise -1. Lets a
     * scan that walks source text a token at a time step over a comment without first collecting
     * every comment in the file. The caller is responsible for not calling this inside a string
     * literal; {@link #scan} does that by skipping literals itself.
     */
    public int skipComment(final String source, final int pos) {
        Objects.requireNonNull(source, "source");
        final var n = source.length();
        if (pos < 0 || pos + 1 >= n || source.charAt(pos) != '/') {
            return -1;
        }
        final var next = source.charAt(pos + 1);
        if (next == '/') {
            var i = pos + 2;
            while (i < n && source.charAt(i) != '\n' && source.charAt(i) != '\r') {
                i++;
            }
            return i;
        }
        if (next == '*') {
            var i = pos + 2;
            while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                i++;
            }
            return Math.min(i + 2, n);
        }
        return -1;
    }

    /**
     * {@code source} with every comment removed and nothing else touched. Goes through
     * {@link #scan}, so a {@code //} or {@code /*} inside a string literal, a char literal or a
     * text block is left alone — which a regex over the same text is not.
     */
    public String withoutComments(final String source) {
        final var comments = scan(source);
        if (comments.isEmpty()) {
            return source;
        }
        final var out = new StringBuilder(source.length());
        var cursor = 0;
        for (var comment : comments) {
            out.append(source, cursor, comment.start());
            cursor = comment.end();
        }
        return out.append(source, cursor, source.length()).toString();
    }

    private int columnOf(final String source, final int pos) {
        var col = 0;
        var i = pos - 1;
        while (i >= 0 && source.charAt(i) != '\n' && source.charAt(i) != '\r') {
            i--;
            col++;
        }
        return col;
    }

    private int skipStringLiteral(final String source, final int open, final int n) {
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

    private int skipCharLiteral(final String source, final int open, final int n) {
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

    private int skipTextBlock(final String source, final int open, final int n) {
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
}
