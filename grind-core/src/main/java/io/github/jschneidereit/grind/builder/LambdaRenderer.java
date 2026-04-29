package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.VariableTree;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import io.github.jschneidereit.grind.doc.Doc;

final class LambdaRenderer {

    static Doc render(final LambdaExpressionTree node, final Recursor recursor) {
        final var paramList = renderParamList(node.getParameters(), recursor);
        return switch (node.getBodyKind()) {
            case EXPRESSION -> renderExpressionBody(node, paramList, recursor);
            case STATEMENT -> renderBlockBody(node, paramList, recursor);
        };
    }

    private static Doc renderExpressionBody(final LambdaExpressionTree node, final Doc paramList, final Recursor recursor) {
        return new Doc.Concat(List.of(paramList, new Doc.Text(" -> "), recursor.scan(node.getBody())));
    }

    private static Doc renderBlockBody(final LambdaExpressionTree node, final Doc paramList, final Recursor recursor) {
        final var block = (BlockTree) node.getBody();
        final var stmts = block.getStatements().stream()
            .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
            .toList();
        final Doc header = new Doc.Concat(List.of(paramList, new Doc.Text(" ->")));
        return BlockRenderer.buildBlock(header, stmts, List.of());
    }

    private static Doc renderParamList(final List<? extends VariableTree> params, final Recursor recursor) {
        if (params.size() == 1 && params.get(0).getType() == null) {
            return new Doc.Text(params.get(0).getName().toString());
        }
        final var interior = Doc.intersperse(new Doc.Text(", "), params.stream()
            .<Doc>map(p -> renderParam(p, recursor)));
        return new Doc.Concat(Stream.concat(
            Stream.<Doc>of(new Doc.Text("(")),
            Stream.concat(interior, Stream.<Doc>of(new Doc.Text(")")))
        ));
    }

    private static Doc renderParam(final VariableTree param, final Recursor recursor) {
        if (param.getType() == null) {
            return new Doc.Text(param.getName().toString());
        }
        return new Doc.Concat(List.of(
            recursor.scan(param.getType()),
            new Doc.Text(" " + param.getName())));
    }

    private LambdaRenderer() {}
}
