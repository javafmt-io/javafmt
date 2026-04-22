package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.TryTree;

import java.util.List;
import java.util.stream.Stream;

final class TryRenderer {

    static Doc render(final TryTree node, final Recursor recursor) {
        final var tryStmts = BlockRenderer.blockStmts(node.getBlock(), recursor);
        final var catchStream = node.getCatches().stream().flatMap(c -> catchParts(c, recursor));
        final var finallyBlock = node.getFinallyBlock();
        final var finallyStream = finallyBlock == null ? Stream.<Doc>empty() : Stream.concat(
            Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("} finally {")),
            BlockRenderer.blockStmts(finallyBlock, recursor).stream()
                .map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s))))
        );
        return new Doc.Concat(Stream.of(
            BlockRenderer.blockParts("try", tryStmts),
            catchStream,
            finallyStream,
            Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("}"))
        ).flatMap(s -> s));
    }

    private static Stream<Doc> catchParts(final CatchTree c, final Recursor recursor) {
        final var param = c.getParameter();
        final var header = "} catch (" + param.getType() + " " + param.getName() + ") {";
        return Stream.concat(
            Stream.<Doc>of(new Doc.HardLine(), new Doc.Text(header)),
            BlockRenderer.blockStmts(c.getBlock(), recursor).stream()
                .map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s))))
        );
    }

    private TryRenderer() {}
}
