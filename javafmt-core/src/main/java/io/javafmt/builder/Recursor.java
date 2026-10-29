package io.javafmt.builder;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import org.jspecify.annotations.Nullable;

interface Recursor {

    Doc scan(Tree node);

    @Nullable Doc scanNullable(Tree node);

    /**
     * Scans {@code node} with compact context active for the subtree. While compact, nested
     * method-chain and logical-chain renderers skip their 80-char break heuristics and ignore
     * author-broke layout — they only break when a hard newline or the 150-char line width
     * forces it. Used by the logical-chain renderer when it has already chosen to break on
     * every operator, so each operand renders on a single line if it possibly can.
     */
    Doc scanCompact(Tree node);

    boolean inCompactContext();

    void emitWarning(String message, Tree at);

    boolean isVarargs(VariableTree param);

    boolean isCompactConstructor(MethodTree ctor);

    /**
     * Whether {@code member} is an enum constant rather than a field declared in the enum body.
     * Both are {@link VariableTree}s and both may carry a {@code new} initializer, so the shape
     * of the tree alone cannot tell them apart.
     */
    boolean isEnumConstant(VariableTree member);

    /**
     * Whether the source text immediately following {@code node} contains a newline before
     * the next non-whitespace character. Used to preserve annotation layout: an annotation
     * that the author put on its own line stays on its own line; an inline annotation
     * stays inline.
     */
    boolean isFollowedByNewline(Tree node);

    /**
     * Whether the author left a blank line between two consecutive declarations or statements.
     * Between plain fields and between statements of a method body, javafmt keeps the author's
     * grouping rather than imposing one: those blank lines are how a reader is told which
     * declarations belong together.
     */
    boolean blankLineBetween(Tree before, Tree after);

    /**
     * Whether {@code node} carries a Javadoc comment. A field with Javadoc is separated like a
     * method rather than packed against its neighbours, which is what google-java-format does.
     */
    boolean hasJavadoc(Tree node);

    /**
     * The exact source text of {@code node}. Used where reconstructing a node from the parse tree
     * would lose something the tree does not carry — annotations render this way because javac's
     * pretty-printer silently drops interior comments and normalizes away punctuation the lint
     * pass deliberately added.
     */
    String sourceOf(Tree node);
}
