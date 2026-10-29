package io.javafmt.parser;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * {@link TreePathScanner} on top of {@link ChainScanner}: {@link #getCurrentPath()} is the path to
 * the node being visited, including while a chain is walked with a loop.
 */
public abstract class ChainPathScanner<R extends @Nullable Object, P extends @Nullable Object> extends ChainScanner<R, P> {

    @Nullable private TreePath path;

    private final IdentityHashMap<Tree, TreePath> chainPaths = new IdentityHashMap<>();

    @Nullable public R scan(final TreePath path, @Nullable final P p) {
        this.path = path;
        try {
            return path.getLeaf().accept(this, p);
        } finally {
            this.path = null;
        }
    }

    @Override
    @Nullable
    public R scan(@Nullable final Tree tree, @Nullable final P p) {
        if (tree == null) {
            return null;
        }
        final var enclosing = path;
        path = new TreePath(enclosing, tree);
        try {
            return tree.accept(this, p);
        } finally {
            path = enclosing;
        }
    }

    public TreePath getCurrentPath() {
        return Objects.requireNonNull(path, "getCurrentPath() outside a scan");
    }

    @Override
    void enterChain(final List<? extends Tree> spine) {
        var parent = getCurrentPath();
        chainPaths.put(spine.getFirst(), parent);
        for (final var link : spine.subList(1, spine.size())) {
            parent = new TreePath(parent, link);
            chainPaths.put(link, parent);
        }
    }

    @Override
    void exitChain(final List<? extends Tree> spine) {
        spine.forEach(chainPaths::remove);
    }

    @Override
    <T extends @Nullable Object> T at(final Tree link, final Supplier<T> work) {
        final var enclosing = path;
        path = Objects.requireNonNull(chainPaths.get(link), "link outside its chain");
        try {
            return work.get();
        } finally {
            path = enclosing;
        }
    }
}
