package io.github.jschneidereit.grind.parser;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ParsedUnit(
        CompilationUnitTree tree,
        List<CommentToken> fileHeader,
        List<CommentToken> fileFooter,
        Map<Tree, List<CommentToken>> leading,
        Map<Tree, List<CommentToken>> trailing,
        Map<Tree, List<CommentToken>> interior) {

    @SuppressWarnings("IdentityHashMapUsage")
    public ParsedUnit {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(fileHeader, "fileHeader");
        Objects.requireNonNull(fileFooter, "fileFooter");
        Objects.requireNonNull(leading, "leading");
        Objects.requireNonNull(trailing, "trailing");
        Objects.requireNonNull(interior, "interior");
        fileHeader = List.copyOf(fileHeader);
        fileFooter = List.copyOf(fileFooter);
        final IdentityHashMap<Tree, List<CommentToken>> leadingCopy = new IdentityHashMap<>(leading);
        final IdentityHashMap<Tree, List<CommentToken>> trailingCopy = new IdentityHashMap<>(trailing);
        final IdentityHashMap<Tree, List<CommentToken>> interiorCopy = new IdentityHashMap<>(interior);
        leading = Collections.unmodifiableMap(leadingCopy);
        trailing = Collections.unmodifiableMap(trailingCopy);
        interior = Collections.unmodifiableMap(interiorCopy);
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
}
