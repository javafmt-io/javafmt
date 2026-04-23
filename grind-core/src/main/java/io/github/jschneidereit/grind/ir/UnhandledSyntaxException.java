package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.Tree;

public final class UnhandledSyntaxException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Tree.Kind kind;

    UnhandledSyntaxException(final Tree tree) {
        super("No dedicated renderer for " + tree.getKind() + " (" + tree.getClass().getSimpleName() + "); would fall back to toString()");
        this.kind = tree.getKind();
    }

    public Tree.Kind kind() {
        return kind;
    }
}
