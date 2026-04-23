package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.Tree;

import io.github.jschneidereit.grind.parser.CommentToken;

import java.util.List;

interface LeadingCommentAttacher {

    Doc attach(Tree node, Doc doc);

    List<CommentToken> interior(Tree node);
}
