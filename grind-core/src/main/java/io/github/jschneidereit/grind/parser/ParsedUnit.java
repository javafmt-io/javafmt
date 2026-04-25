package io.github.jschneidereit.grind.parser;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;

import io.github.jschneidereit.grind.Position;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Parsed compilation unit with comments attached to AST nodes.
 *
 * <p>Comment-channel maps ({@code leading}, {@code trailing}, {@code interior}, {@code tail})
 * are typed as {@link IdentityHashMap} because lookup must be by reference identity, not
 * equality: javac may produce {@code .equals}-equal {@link Tree} instances for distinct
 * source positions.
 */
public record ParsedUnit(
        CompilationUnitTree tree,
        SourcePositions sourcePositions,
        List<CommentToken> fileHeader,
        List<CommentToken> fileFooter,
        IdentityHashMap<Tree, List<CommentToken>> leading,
        IdentityHashMap<Tree, List<CommentToken>> trailing,
        IdentityHashMap<Tree, List<CommentToken>> interior,
        IdentityHashMap<Tree, List<CommentToken>> tail) {

    public ParsedUnit {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(sourcePositions, "sourcePositions");
        Objects.requireNonNull(fileHeader, "fileHeader");
        Objects.requireNonNull(fileFooter, "fileFooter");
        Objects.requireNonNull(leading, "leading");
        Objects.requireNonNull(trailing, "trailing");
        Objects.requireNonNull(interior, "interior");
        Objects.requireNonNull(tail, "tail");
        fileHeader = List.copyOf(fileHeader);
        fileFooter = List.copyOf(fileFooter);
        leading = new IdentityHashMap<>(leading);
        trailing = new IdentityHashMap<>(trailing);
        interior = new IdentityHashMap<>(interior);
        tail = new IdentityHashMap<>(tail);
    }

    public List<CommentToken> leadingOf(final Tree node) {
        return leading.getOrDefault(node, List.of());
    }

    public List<CommentToken> trailingOf(final Tree node) {
        return trailing.getOrDefault(node, List.of());
    }

    public List<CommentToken> interiorOf(final Tree node) {
        return interior.getOrDefault(node, List.of());
    }

    public List<CommentToken> tailOf(final Tree node) {
        return tail.getOrDefault(node, List.of());
    }

    public Position positionOf(final Tree node) {
        return JavaParser.positionOf(sourcePositions, tree, node);
    }
}
