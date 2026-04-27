package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

interface Recursor {

    Doc scan(Tree node);

    void emitWarning(String message, Tree at);

    boolean isVarargs(VariableTree param);
}
