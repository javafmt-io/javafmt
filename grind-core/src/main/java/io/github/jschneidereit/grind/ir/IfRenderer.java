package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.IfTree;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

final class IfRenderer {

    private IfRenderer() {}

    static Doc render(final IfTree node, final Recursor recursor) {
        // javac wraps the condition in JCParens, so toString() already includes the outer ()
        final var cond = node.getCondition().toString();
        final var thenStmts = BlockRenderer.blockStmts(node.getThenStatement(), recursor);
        if (node.getElseStatement() == null) {
            return BlockRenderer.buildBlock("if " + cond, thenStmts);
        }
        if (node.getElseStatement() instanceof IfTree elseIf) {
            final var elseIfDoc = Objects.requireNonNull(recursor.scan(elseIf));
            return new Doc.Concat(Stream.concat(
                Stream.concat(
                    BlockRenderer.blockParts("if " + cond, thenStmts),
                    Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("} else "))
                ),
                Stream.<Doc>of(elseIfDoc)
            ));
        }
        final var elseStmts = BlockRenderer.blockStmts(node.getElseStatement(), recursor);
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                BlockRenderer.blockParts("if " + cond, thenStmts),
                Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("} else {"))
            ),
            Stream.concat(
                elseStmts.stream()
                    .<Doc>map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s)))),
                Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("}"))
            )
        ));
    }
}
