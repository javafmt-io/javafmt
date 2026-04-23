package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.Tree;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
interface Recursor {

    @Nullable Doc scan(Tree node);

    default Doc scanOrText(final Tree node) {
        return Optional.ofNullable(scan(node))
            .orElseGet(() -> new Doc.Text(node.toString()));
    }
}
