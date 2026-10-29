package io.javafmt.lint;

import com.sun.source.tree.VariableTree;
import io.javafmt.JavaModifiers;
import io.javafmt.parser.CommentScanner;
import java.util.List;
import javax.lang.model.element.Modifier;
import lombok.experimental.UtilityClass;

/**
 * Modifier helpers shared across lint rules: the canonical JLS order, whether a declaration is
 * already {@code final}, and source-level matching of a modifier keyword at a given position.
 */
@UtilityClass
final class Modifiers {

    List<Modifier> canonicalOrder() {
        return JavaModifiers.JLS_ORDER;
    }

    boolean hasFinal(final VariableTree node) {
        return node.getModifiers().getFlags().contains(Modifier.FINAL);
    }

    /**
     * The position just past the annotation whose {@code @} sits at {@code afterAt - 1}.
     *
     * <p>The name may be qualified — {@code @jdk.internal.RequiresIdentity} is one annotation, not
     * {@code @jdk} followed by something else — and the argument list may contain parentheses
     * inside a string or a comment, neither of which closes it. Getting either wrong returns a
     * position in the middle of the annotation, and a caller rewriting the span up to that point
     * deletes the rest of it.
     */
    int skipAnnotation(final String source, final int afterAt) {
        var pos = skipQualifiedName(source, afterAt);
        if (pos >= source.length() || source.charAt(pos) != '(') {
            return pos;
        }
        var depth = 0;
        while (pos < source.length()) {
            final var commentEnd = CommentScanner.skipComment(source, pos);
            if (commentEnd >= 0) {
                pos = commentEnd;
                continue;
            }
            final var c = source.charAt(pos);
            if (c == '"' || c == '\'') {
                pos = skipLiteral(source, pos, c);
                continue;
            }
            pos++;
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return pos;
            }
        }
        return pos;
    }

    private int skipQualifiedName(final String source, final int from) {
        var pos = from;
        while (pos < source.length() && (Character.isJavaIdentifierPart(source.charAt(pos)) || isDotBetweenNameParts(source, pos))) {
            pos++;
        }
        return pos;
    }

    /** A {@code .} only continues the name when another name part follows it. */
    private boolean isDotBetweenNameParts(final String source, final int pos) {
        return source.charAt(pos) == '.'
            && pos + 1 < source.length()
            && Character.isJavaIdentifierStart(source.charAt(pos + 1));
    }

    private int skipLiteral(final String source, final int open, final char quote) {
        var pos = open + 1;
        while (pos < source.length()) {
            final var c = source.charAt(pos);
            if (c == '\\') {
                pos += 2;
                continue;
            }
            pos++;
            if (c == quote) {
                return pos;
            }
        }
        return pos;
    }

    /**
     * If {@code keyword} starts at {@code from} in {@code source} and is not itself a prefix of a
     * longer identifier (so {@code finalize} does not match {@code final}), returns the position
     * immediately after it; otherwise -1.
     */
    int skipModifierKeyword(final String source, final int from, final String keyword) {
        if (!source.startsWith(keyword, from)) {
            return -1;
        }
        final var end = from + keyword.length();
        return end < source.length() && Character.isJavaIdentifierPart(source.charAt(end)) ? -1 : end;
    }
}
