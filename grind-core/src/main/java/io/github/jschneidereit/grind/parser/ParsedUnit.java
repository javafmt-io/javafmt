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
 * use identity-keyed lookup because javac may produce {@code .equals}-equal {@link Tree}
 * instances for distinct source positions.
 */
public final class ParsedUnit {

    private final CompilationUnitTree tree;
    private final String source;
    private final SourcePositions sourcePositions;
    private final List<CommentToken> fileHeader;
    private final List<CommentToken> fileFooter;
    private final IdentityHashMap<Tree, List<CommentToken>> leading;
    private final IdentityHashMap<Tree, List<CommentToken>> trailing;
    private final IdentityHashMap<Tree, List<CommentToken>> interior;
    private final IdentityHashMap<Tree, List<CommentToken>> tail;

    public ParsedUnit(
            final CompilationUnitTree tree,
            final String source,
            final SourcePositions sourcePositions,
            final List<CommentToken> fileHeader,
            final List<CommentToken> fileFooter,
            final IdentityHashMap<Tree, List<CommentToken>> leading,
            final IdentityHashMap<Tree, List<CommentToken>> trailing,
            final IdentityHashMap<Tree, List<CommentToken>> interior,
            final IdentityHashMap<Tree, List<CommentToken>> tail) {
        this.tree = Objects.requireNonNull(tree, "tree");
        this.source = Objects.requireNonNull(source, "source");
        this.sourcePositions = Objects.requireNonNull(sourcePositions, "sourcePositions");
        this.fileHeader = List.copyOf(Objects.requireNonNull(fileHeader, "fileHeader"));
        this.fileFooter = List.copyOf(Objects.requireNonNull(fileFooter, "fileFooter"));
        this.leading = deepCopy(Objects.requireNonNull(leading, "leading"));
        this.trailing = deepCopy(Objects.requireNonNull(trailing, "trailing"));
        this.interior = deepCopy(Objects.requireNonNull(interior, "interior"));
        this.tail = deepCopy(Objects.requireNonNull(tail, "tail"));
    }

    public CompilationUnitTree tree() {
        return tree;
    }

    public String source() {
        return source;
    }

    public SourcePositions sourcePositions() {
        return sourcePositions;
    }

    public List<CommentToken> fileHeader() {
        return fileHeader;
    }

    public List<CommentToken> fileFooter() {
        return fileFooter;
    }

    public String sourceOf(final Tree node) {
        final var start = sourcePositions.getStartPosition(tree, node);
        final var end = sourcePositions.getEndPosition(tree, node);
        if (start < 0 || end < 0 || end > source.length() || start > end) {
            throw new IllegalStateException("no source positions for " + node.getKind() + ": " + node);
        }
        return source.substring((int) start, (int) end);
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

    private static IdentityHashMap<Tree, List<CommentToken>> deepCopy(
            final IdentityHashMap<Tree, List<CommentToken>> src) {
        final var copy = new IdentityHashMap<Tree, List<CommentToken>>(src.size());
        src.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return copy;
    }
}
