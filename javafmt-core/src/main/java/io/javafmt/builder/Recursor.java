package io.javafmt.builder;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import io.javafmt.parser.TreeFacts;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

interface Recursor extends LeadingCommentAttacher {

    TreeFacts facts();

    Doc scan(Tree node);

    Doc scanBeforeComments(Tree node, UnaryOperator<Doc> adjust);

    Doc scanLeavingTrailing(Tree node, UnaryOperator<Doc> adjust);

    MethodChainRenderer.ChainDocs scanChain(MethodInvocationTree node);

    <T> T renderingEachTreeOnce(Supplier<T> layouts);

    void emitWarning(String message, Tree at);

    boolean hasLeadingComments(Tree node);

    /**
     * The exact source text of {@code node}. Used where reconstructing a node from the parse tree
     * would lose something the tree does not carry, such as the separator an author wrote after
     * the last enum constant.
     */
    String sourceOf(Tree node);

    CharSequence sourceSpan(Tree node);
}
