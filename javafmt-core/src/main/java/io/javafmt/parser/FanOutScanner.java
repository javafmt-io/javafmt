package io.javafmt.parser;

import com.sun.source.tree.Tree;

import java.util.List;
import java.util.Objects;

/**
 * Walks a tree once for any number of scanners. Each scanner is made {@linkplain ChainScanner#shallow
 * shallow}, then is handed every node in the order a walk of its own would reach it, with the same
 * {@link ChainPathScanner#getCurrentPath path} a walk of its own would report, so a scanner cannot tell
 * it is sharing the walk. That holds only for a scanner that does all its work before it calls {@code
 * super}: anything it runs after {@code super} returns happens as soon as its own visit method has
 * handled the node, not once the node's children are done.
 */
public final class FanOutScanner extends ChainPathScanner<Void, Void> {

    private final List<? extends ChainScanner<Void, Void>> scanners;

    private FanOutScanner(final List<? extends ChainScanner<Void, Void>> scanners) {
        this.scanners = scanners;
    }

    public static void walk(final Tree root, final List<? extends ChainScanner<Void, Void>> scanners) {
        Objects.requireNonNull(root, "root");
        final var shared = List.copyOf(Objects.requireNonNull(scanners, "scanners"));
        shared.forEach(ChainScanner::shallow);
        new FanOutScanner(shared).scan(root, null);
    }

    @Override
    void visiting(final Tree node) {
        final var path = getCurrentPath();
        for (final var scanner : scanners) {
            scanner.positionedAt(path);
            node.accept(scanner, null);
        }
    }
}
