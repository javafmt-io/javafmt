package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.Tree;

interface Recursor {

    Doc scan(Tree node);

    void emitWarning(String message, Tree at);
}
