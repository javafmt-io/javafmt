package io.javafmt.builder;

import com.sun.source.tree.IfTree;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import io.javafmt.doc.Doc;

final class IfRenderer {

    static Doc render(final IfTree node, final Recursor recursor) {
        // javac wraps the condition in JCParens, so the rendered condition already includes the outer ()
        final var condDoc = recursor.scan(node.getCondition());
        final var header = new Doc.Concat(List.of(new Doc.Text("if "), condDoc));
        final var thenStmts = BlockRenderer.blockStmts(node.getThenStatement(), recursor);
        if (node.getElseStatement() == null) {
            return BlockRenderer.buildBlock(header, thenStmts, List.of());
        }
        if (node.getElseStatement() instanceof IfTree elseIf) {
            final var elseIfDoc = Objects.requireNonNull(recursor.scan(elseIf));
            return new Doc.Concat(Stream.concat(
                Stream.concat(
                    BlockRenderer.blockParts(header, thenStmts),
                    Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("} else "))
                ),
                Stream.<Doc>of(elseIfDoc)
            ));
        }
        final var elseStmts = BlockRenderer.blockStmts(node.getElseStatement(), recursor);
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                BlockRenderer.blockParts(header, thenStmts),
                Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("} else {"))
            ),
            Stream.concat(
                elseStmts.stream()
                    .<Doc>map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s)))),
                Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("}"))
            )
        ));
    }

    private IfRenderer() {}
}
