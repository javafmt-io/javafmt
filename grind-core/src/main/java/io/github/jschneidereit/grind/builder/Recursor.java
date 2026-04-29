package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

import io.github.jschneidereit.grind.doc.Doc;

import org.jspecify.annotations.Nullable;

interface Recursor {

    Doc scan(Tree node);

    @Nullable Doc scanNullable(Tree node);

    void emitWarning(String message, Tree at);

    boolean isVarargs(VariableTree param);

    boolean isCompactConstructor(MethodTree ctor);
}
