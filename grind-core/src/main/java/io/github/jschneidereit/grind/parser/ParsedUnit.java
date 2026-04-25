package io.github.jschneidereit.grind.parser;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;

import io.github.jschneidereit.grind.Position;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ParsedUnit(
        CompilationUnitTree tree,
        SourcePositions sourcePositions,
        List<CommentToken> fileHeader,
        List<CommentToken> fileFooter,
        Map<Tree, List<CommentToken>> leading,
        Map<Tree, List<CommentToken>> trailing,
        Map<Tree, List<CommentToken>> interior,
        Map<Tree, List<CommentToken>> tail) {

    @SuppressWarnings("IdentityHashMapUsage")
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
        final IdentityHashMap<Tree, List<CommentToken>> leadingCopy = new IdentityHashMap<>(leading);
        final IdentityHashMap<Tree, List<CommentToken>> trailingCopy = new IdentityHashMap<>(trailing);
        final IdentityHashMap<Tree, List<CommentToken>> interiorCopy = new IdentityHashMap<>(interior);
        final IdentityHashMap<Tree, List<CommentToken>> tailCopy = new IdentityHashMap<>(tail);
        leading = Collections.unmodifiableMap(leadingCopy);
        trailing = Collections.unmodifiableMap(trailingCopy);
        interior = Collections.unmodifiableMap(interiorCopy);
        tail = Collections.unmodifiableMap(tailCopy);
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
