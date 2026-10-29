package io.javafmt.parser;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.util.Objects;

/** javac-shape predicates that need source positions to answer questions the tree alone cannot. */
public final class TreeFacts {

    private final ParsedUnit unit;

    public TreeFacts(final ParsedUnit unit) {
        this.unit = Objects.requireNonNull(unit, "unit");
    }

    /**
     * javac models {@code T... x} as {@code ArrayTypeTree(T)} with the {@code ...} flag on the
     * parameter symbol, but javac's source-position range for the type tree spans the original
     * user-written form — {@code T...} for varargs vs. {@code T[]} for a regular array — so the
     * suffix is decisive without needing the symbol model.
     */
    public boolean isVarargs(final VariableTree param) {
        // A type-use annotation on the varargs array wraps the ArrayTypeTree, so testing the type
        // directly reported `String @N ... args` as an ordinary array and the `...` was lost.
        final var type = param.getType() instanceof AnnotatedTypeTree annotated ? annotated.getUnderlyingType() : param.getType();
        if (!(type instanceof ArrayTypeTree)) {
            return false;
        }
        return unit.sourceOf(param.getType()).endsWith("...");
    }

    /**
     * javac synthesizes parameter trees for a record's compact canonical constructor by
     * reusing the header components, whose source positions sit before the constructor body.
     * A regular constructor's parameters are within its own range. So a parameter positioned
     * before the constructor's start is decisive evidence of compact form.
     */
    public boolean isCompactConstructor(final MethodTree ctor) {
        if (!ctor.getName().contentEquals("<init>")) {
            return false;
        }
        final var ctorStart = unit.sourcePositions().getStartPosition(unit.tree(), ctor);
        if (ctorStart < 0) {
            return false;
        }
        return ctor.getParameters()
                .stream()
                .mapToLong(p -> unit.sourcePositions().getStartPosition(unit.tree(), p))
                .anyMatch(pos -> pos >= 0 && pos < ctorStart);
    }

    /**
     * An enum constant is written without a type, so javac synthesizes one naming the enum.
     * That synthetic tree has no end position, while a field declared in the enum body has a
     * type the author actually wrote and therefore a real end position. The tree shape is not
     * decisive on its own: {@code static final Object LOCK = new Object();} in an enum body is
     * a {@link VariableTree} with a {@code NewClassTree} initializer, exactly like a constant
     * with arguments or a class body.
     */
    public boolean isEnumConstant(final VariableTree member) {
        final var type = member.getType();
        if (type == null) {
            return false;
        }
        return unit.sourcePositions().getEndPosition(unit.tree(), type) < 0;
    }

    /**
     * Whether the author left a blank line between {@code before} and {@code after}. Measured up to
     * the first non-whitespace character after {@code before}, so a blank line the author put in
     * front of a comment counts as a blank line in front of the declaration the comment belongs to.
     */
    public boolean blankLineBetween(final Tree before, final Tree after) {
        final var declared = (int) unit.sourcePositions().getEndPosition(unit.tree(), before);
        final var to = (int) unit.sourcePositions().getStartPosition(unit.tree(), after);
        if (declared < 0 || to < declared) {
            return false;
        }
        // A trailing comment belongs to the declaration it follows, so the gap starts after it —
        // otherwise `int x = 1; // note` followed by a blank line reads as no blank line at all.
        final var from = unit.trailingOf(before)
                .stream()
                .mapToInt(CommentToken::end)
                .max()
                .orElse(declared);
        final var leading = unit.leadingOf(after);
        final var until = leading.isEmpty() ? to : Math.min(to, leading.getFirst().start());
        return newlinesIn(unit.source(), from, Math.max(from, until)) > 1;
    }

    private static int newlinesIn(final String source, final int from, final int to) {
        var count = 0;
        for (var i = from; i < to; i++) {
            if (source.charAt(i) == '\n') {
                count++;
            } else if (!Character.isWhitespace(source.charAt(i))) {
                return count;
            }
        }
        return count;
    }

    /** Whether {@code node} carries a Javadoc comment. */
    public boolean hasJavadoc(final Tree node) {
        return unit.leadingOf(node).stream().anyMatch(c -> c.text().startsWith("/**"));
    }

    /**
     * Whether the author started a new line after {@code node}, looking past any comment they
     * wrote in between. A {@code //} comment always ends its line, and a block comment is
     * stepped over: without that, {@code s.strip() // step one} reported no line break, and the
     * chain the author had spread over several lines was collapsed onto one.
     */
    public boolean isFollowedByNewline(final Tree node) {
        final var endPos = (int) unit.sourcePositions().getEndPosition(unit.tree(), node);
        if (endPos < 0) {
            return false;
        }
        final var src = unit.source();
        var i = endPos;
        while (i < src.length()) {
            final var c = src.charAt(i);
            if (c == '\n') {
                return true;
            }
            if (Character.isWhitespace(c)) {
                i++;
            } else if (src.startsWith("//", i)) {
                return true;
            } else if (src.startsWith("/*", i)) {
                final var close = src.indexOf("*/", i + 2);
                if (close < 0) {
                    return false;
                }
                i = close + 2;
            } else {
                return false;
            }
        }
        return false;
    }
}
