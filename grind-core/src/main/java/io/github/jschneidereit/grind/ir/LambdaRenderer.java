package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.VariableTree;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class LambdaRenderer {

    static Doc render(final LambdaExpressionTree node, final Recursor recursor) {
        final var paramList = renderParamList(node.getParameters());
        return switch (node.getBodyKind()) {
            case EXPRESSION -> renderExpressionBody(node, paramList, recursor);
            case STATEMENT -> renderBlockBody(node, paramList, recursor);
        };
    }

    private static Doc renderExpressionBody(final LambdaExpressionTree node, final String paramList, final Recursor recursor) {
        final var body = node.getBody();
        final var scanned = recursor.scan(body);
        final var bodyDoc = scanned != null ? scanned : new Doc.Text(body.toString());
        return new Doc.Concat(List.of(new Doc.Text(paramList + " -> "), bodyDoc));
    }

    private static Doc renderBlockBody(final LambdaExpressionTree node, final String paramList, final Recursor recursor) {
        final var block = (BlockTree) node.getBody();
        final var stmts = block.getStatements().stream()
            .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
            .toList();
        return BlockRenderer.buildBlock(paramList + " ->", stmts);
    }

    private static String renderParamList(final List<? extends VariableTree> params) {
        if (params.size() == 1 && params.get(0).getType() == null) {
            return params.get(0).getName().toString();
        }
        return "(" + params.stream().map(LambdaRenderer::renderParam).collect(Collectors.joining(", ")) + ")";
    }

    private static String renderParam(final VariableTree param) {
        return param.getType() == null ? param.getName().toString() : param.toString();
    }

    private LambdaRenderer() {}
}
