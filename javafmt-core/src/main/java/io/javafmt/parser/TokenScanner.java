package io.javafmt.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * Lexes Java source into the token stream that must survive formatting: identifiers,
 * keywords, literals, and operator/punctuation marks. Whitespace and comments are skipped —
 * comments are compared separately (see {@link CommentScanner}) because the formatter
 * reflows them through their own channels.
 *
 * <p>This is a comparison lexer for {@link RoundTripVerifier}, not a compiler front end.
 * Two deliberate simplifications:
 *
 * <ul>
 *   <li>Operators are lexed with maximal munch, so {@code --} is one token and {@code - -} is
 *       two. Emitting them a mark at a time — which this used to do — made those two
 *       indistinguishable once whitespace was skipped, and that is how {@code - -x} printed as
 *       {@code --x} without the verifier noticing.</li>
 *   <li>A numeric literal is any run of letters, digits, underscores and dots that starts
 *       with a digit (or a dot followed by a digit), with {@code +}/{@code -} absorbed after
 *       an exponent marker. Malformed literals lex as one blob rather than being rejected;
 *       javac has already accepted the source by the time this runs.</li>
 * </ul>
 *
 * <p>Written as an explicit scan loop rather than a stream pipeline, matching
 * {@link CommentScanner}: a lexer's state is inherently positional.
 */
@UtilityClass
public final class TokenScanner {

    public List<SourceToken> scan(final String source) {
        Objects.requireNonNull(source, "source");
        final var tokens = new ArrayList<SourceToken>();
        final var n = source.length();
        var i = 0;
        while (i < n) {
            final var c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                i = skipLineComment(source, i, n);
                continue;
            }
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i = skipBlockComment(source, i, n);
                continue;
            }
            final var end = tokenEnd(source, i, n, c);
            tokens.add(new SourceToken(i, end, source.substring(i, end)));
            i = end;
        }
        return List.copyOf(tokens);
    }

    private int tokenEnd(final String source, final int start, final int n, final char c) {
        if (c == '"') {
            return start + 2 < n
                && source.charAt(start + 1) == '"'
                && source.charAt(start + 2) == '"'
                    ? endOfTextBlock(source, start, n)
                    : endOfStringLiteral(source, start, n);
        }
        if (c == '\'') {
            return endOfCharLiteral(source, start, n);
        }
        if (isDigit(c) || (c == '.' && start + 1 < n && isDigit(source.charAt(start + 1)))) {
            return endOfNumericLiteral(source, start, n);
        }
        if (Character.isJavaIdentifierStart(c)) {
            return endOfIdentifier(source, start, n);
        }
        return endOfOperator(source, start, n);
    }

    /**
     * The longest Java operator or punctuation mark starting at {@code start}. Longest first, so
     * {@code >>>=} is not mistaken for {@code >>>} — and, the point of the exercise, {@code --} is
     * not two {@code -} marks.
     */
    private int endOfOperator(final String source, final int start, final int n) {
        for (var operator : OPERATORS) {
            if (start + operator.length() <= n && source.startsWith(operator, start)) {
                return start + operator.length();
            }
        }
        return start + 1;
    }

    /**
     * Every multi-character operator in the language, longest first. {@code >>} and {@code >>>} are
     * deliberately absent: javac splits them itself so that nested generics such as
     * {@code List<List<String>>} close, and a lexer that joined them here would disagree with the
     * only reader that matters.
     */
    private final List<String> OPERATORS = List.of(
            ">>>=",
            "<<=",
            ">>=",
            "...",
            "->",
            "::",
            "++",
            "--",
            "&&",
            "||",
            "==",
            "!=",
            "<=",
            ">=",
            "+=",
            "-=",
            "*=",
            "/=",
            "%=",
            "&=",
            "|=",
            "^=",
            "<<");

    private boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    private int endOfIdentifier(final String source, final int start, final int n) {
        var i = start + 1;
        while (i < n && Character.isJavaIdentifierPart(source.charAt(i))) {
            i++;
        }
        return i;
    }

    private int endOfNumericLiteral(final String source, final int start, final int n) {
        final var hex = isHexPrefix(source, start, n);
        var i = start;
        while (i < n) {
            final var c = source.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                i += signContinuesExponent(source, c, i, n, hex) ? 2 : 1;
                continue;
            }
            break;
        }
        return i;
    }

    private boolean isHexPrefix(final String source, final int start, final int n) {
        return start + 1 < n
            && source.charAt(start) == '0'
            && (source.charAt(start + 1) == 'x' || source.charAt(start + 1) == 'X');
    }

    /**
     * Whether the sign at {@code i + 1} continues the exponent marker {@code c} at {@code i}
     * rather than starting the next operator. A sign only belongs to the literal directly after an
     * exponent marker, and which letters those are depends on the base: {@code e} and {@code E} in
     * a decimal literal, {@code p} and {@code P} in a hexadecimal one, where {@code E} is a digit.
     *
     * <p>Treating {@code E} as a marker in both made {@code 0x7E-ch} lex as a single token, so the
     * verifier saw a token stream the output could not match and javafmt refused
     * {@code java/util/regex/ASCII.java} — output it had formatted correctly.
     */
    private boolean signContinuesExponent(final String source, final char c, final int i, final int n, final boolean hex) {
        final var isMarker = hex ? c == 'p' || c == 'P' : c == 'e' || c == 'E';
        return isMarker
            && i + 1 < n
            && (source.charAt(i + 1) == '+' || source.charAt(i + 1) == '-');
    }

    private int endOfStringLiteral(final String source, final int open, final int n) {
        var i = open + 1;
        while (i < n) {
            final var c = source.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == '"') {
                return i + 1;
            }
            if (c == '\n' || c == '\r') {
                return i;
            }
            i++;
        }
        return i;
    }

    private int endOfCharLiteral(final String source, final int open, final int n) {
        var i = open + 1;
        while (i < n) {
            final var c = source.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == '\'') {
                return i + 1;
            }
            if (c == '\n' || c == '\r') {
                return i;
            }
            i++;
        }
        return i;
    }

    private int endOfTextBlock(final String source, final int open, final int n) {
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

    private int skipLineComment(final String source, final int start, final int n) {
        var i = start + 2;
        while (i < n && source.charAt(i) != '\n' && source.charAt(i) != '\r') {
            i++;
        }
        return i;
    }

    private int skipBlockComment(final String source, final int start, final int n) {
        var i = start + 2;
        while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
            i++;
        }
        return Math.min(i + 2, n);
    }
}
