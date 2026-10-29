package io.javafmt.parser;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import io.javafmt.Position;
import java.nio.CharBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parsed compilation unit with comments attached to AST nodes.
 *
 * <p>Comment-channel maps ({@code leading}, {@code trailing}, {@code interior}, {@code tail})
 * use identity-keyed lookup because javac may produce {@code .equals}-equal {@link Tree}
 * instances for distinct source positions.
 */
public record ParsedUnit(
        CompilationUnitTree tree,
        String source,
        SourcePositions sourcePositions,
        List<CommentToken> fileHeader,
        List<CommentToken> fileFooter,
        Map<Tree, List<CommentToken>> leading,
        Map<Tree, List<CommentToken>> trailing,
        Map<Tree, List<CommentToken>> interior,
        Map<Tree, List<CommentToken>> tail) {

    public ParsedUnit {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourcePositions, "sourcePositions");
        fileHeader = List.copyOf(Objects.requireNonNull(fileHeader, "fileHeader"));
        fileFooter = List.copyOf(Objects.requireNonNull(fileFooter, "fileFooter"));
        leading = deepCopy(Objects.requireNonNull(leading, "leading"));
        trailing = deepCopy(Objects.requireNonNull(trailing, "trailing"));
        interior = deepCopy(Objects.requireNonNull(interior, "interior"));
        tail = deepCopy(Objects.requireNonNull(tail, "tail"));
    }

    public String sourceOf(final Tree node) {
        return sourceSpanOf(node).toString();
    }

    public CharSequence sourceSpanOf(final Tree node) {
        final var start = startOf(node);
        final var end = endOf(node);
        if (start < 0 || end < 0 || end > source.length() || start > end) {
            throw new IllegalStateException("no source positions for " + node.getKind() + ": " + node);
        }
        return CharBuffer.wrap(source, start, end);
    }

    public int startOf(final Tree node) {
        return (int) sourcePositions.getStartPosition(tree, node);
    }

    public int endOf(final Tree node) {
        return (int) sourcePositions.getEndPosition(tree, node);
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
        final var offset = startOf(node);
        if (offset < 0) {
            return new Position.Unknown();
        }
        final var lineMap = tree.getLineMap();
        final var line = (int) lineMap.getLineNumber(offset);
        final var column = (int) lineMap.getColumnNumber(offset);
        return new Position.At(line, column, offset);
    }

    private static Map<Tree, List<CommentToken>> deepCopy(final Map<Tree, List<CommentToken>> src) {
        final var copy = new IdentityHashMap<Tree, List<CommentToken>>(src.size());
        src.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(copy);
    }
}
