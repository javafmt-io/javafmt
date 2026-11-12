package io.javafmt.parser;

import com.sun.source.tree.Tree;

import java.util.List;
import java.util.Objects;

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
