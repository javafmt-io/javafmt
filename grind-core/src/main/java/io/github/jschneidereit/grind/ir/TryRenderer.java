package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.TryTree;

import java.util.List;
import java.util.stream.Stream;

final class TryRenderer {

    static Doc render(final TryTree node, final Recursor recursor) {
        final var header = buildHeader(node, recursor);
        final var tryStmts = BlockRenderer.blockStmts(node.getBlock(), recursor);
        final var catchStream = node.getCatches().stream().flatMap(c -> catchParts(c, recursor));
        final var finallyBlock = node.getFinallyBlock();
        final var finallyStream = finallyBlock == null ? Stream.<Doc>empty() : Stream.concat(
            Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("} finally {")),
            BlockRenderer.blockStmts(finallyBlock, recursor).stream()
                .map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s))))
        );
        return new Doc.Concat(Stream.of(
            BlockRenderer.blockParts(header, tryStmts),
            catchStream,
            finallyStream,
            Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("}"))
        ).flatMap(s -> s));
    }

    private static Doc buildHeader(final TryTree node, final Recursor recursor) {
        final var resources = node.getResources();
        if (resources == null || resources.isEmpty()) {
            return new Doc.Text("try");
        }
        final var rendered = Doc.intersperse(new Doc.Text("; "), resources.stream()
            .<Doc>map(r -> BlockRenderer.stripTrailingSemicolonDoc(recursor.scanOrText(r))))
            .toList();
        return new Doc.Concat(Stream.concat(
            Stream.<Doc>of(new Doc.Text("try (")),
            Stream.concat(rendered.stream(), Stream.<Doc>of(new Doc.Text(")")))
        ));
    }

    private static Stream<Doc> catchParts(final CatchTree c, final Recursor recursor) {
        final var param = c.getParameter();
        final Doc header = new Doc.Concat(List.of(
            new Doc.Text("} catch ("),
            recursor.scanOrText(param.getType()),
            new Doc.Text(" " + param.getName() + ") {")));
        return Stream.concat(
            Stream.<Doc>of(new Doc.HardLine(), header),
            BlockRenderer.blockStmts(c.getBlock(), recursor).stream()
                .map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s))))
        );
    }

    private TryRenderer() {}
}
