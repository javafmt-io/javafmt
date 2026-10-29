package io.javafmt.doc;

import com.sun.source.tree.Tree;
import io.javafmt.parser.CommentToken;
import java.util.List;
import java.util.stream.Stream;

public interface LeadingCommentAttacher {

    Doc attach(Tree node, Doc doc);

    List<CommentToken> interior(Tree node);

    /**
     * The comments the author wrote after {@code node} on its own line. {@link #attach} already
     * emits these, so a caller reads them only where it renders the node without going through
     * {@code attach} and has to place them itself.
     */
    default List<CommentToken> trailing(final Tree node) {
        return List.of();
    }

    default List<CommentToken> tail(final Tree node) {
        return List.of();
    }

    /**
     * The comments inside {@code node} that nothing follows — {@link #tail} after its last child,
     * or, for a container with nothing in it, {@link #interior}. The two are the same notion and
     * never both occupied, so a renderer wanting one always wants the other: asking only for
     * {@code tail} drops the comment when the container happens to be empty, and asking only for
     * {@code interior} drops it when it is not. Both mistakes were live, and between them they cost
     * five shapes, three of them files of {@code java.base}.
     */
    default List<CommentToken> trailingInside(final Tree node) {
        return Stream.concat(interior(node).stream(), tail(node).stream()).toList();
    }
}
