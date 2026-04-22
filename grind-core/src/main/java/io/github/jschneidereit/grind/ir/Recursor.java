package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.Tree;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
interface Recursor {
    @Nullable Doc scan(Tree node);
}
