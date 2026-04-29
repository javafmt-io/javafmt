package io.javafmt.doc;

import com.sun.source.tree.Tree;

import io.javafmt.parser.CommentToken;

import java.util.List;

public interface LeadingCommentAttacher {

    Doc attach(Tree node, Doc doc);

    List<CommentToken> interior(Tree node);

    default List<CommentToken> tail(final Tree node) {
        return List.of();
    }
}
