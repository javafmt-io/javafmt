package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
final class LambdaRenderer {

    Doc render(final LambdaExpressionTree node, final Recursor recursor) {
        final var paramList = renderParamList(node, recursor);
        return switch (node.getBodyKind()) {
            case EXPRESSION -> renderExpressionBody(node, paramList, recursor);
            case STATEMENT -> renderBlockBody(node, paramList, recursor);
        };
    }

    private Doc renderExpressionBody(final LambdaExpressionTree node, final Doc paramList, final Recursor recursor) {
        return Doc.concat(paramList, new Doc.Text(" -> "), recursor.scan(node.getBody()));
    }

    private Doc renderBlockBody(final LambdaExpressionTree node, final Doc paramList, final Recursor recursor) {
        final var block = (BlockTree) node.getBody();
        final var stmts = BlockRenderer.blockStmts(block, recursor);
        final var header = Doc.concat(paramList, new Doc.Text(" ->"));
        return BlockRenderer.buildBlock(header, stmts, List.of());
    }

    /**
     * The parameter list, keeping the author's parentheses around a lone inferred parameter.
     * {@code (t) -> …} and {@code t -> …} are the same lambda and javafmt has no opinion on which
     * to write, but it printed the second for both, which drops two tokens the author wrote — the
     * output verifier reported that as corruption and refused the file, which is how
     * {@code java/util/function/Predicate.java} and its three siblings stopped formatting.
     */
    private Doc renderParamList(final LambdaExpressionTree node, final Recursor recursor) {
        final var params = node.getParameters();
        if (params.isEmpty()) {
            return new Doc.Text("()");
        }
        if (params.size() == 1
            && params.getFirst().getType() == null
            && recursor.sourceSpan(node).charAt(0) != '(') {
            return new Doc.Text(Names.of(params.getFirst()));
        }
        return WrappableList.of("(", ",", ")", params.stream().map(p -> renderParam(p, recursor)));
    }

    /**
     * One lambda parameter, modifiers and all. {@code (final String t) -> …} is legal and the
     * modifiers were never read, so the {@code final} was dropped and the file refused.
     */
    private Doc renderParam(final VariableTree param, final Recursor recursor) {
        final var modifiers = new Doc.Text(ModifierRenderer.prefixOf(param.getModifiers(), recursor));
        if (param.getType() == null) {
            return Doc.concat(modifiers, new Doc.Text(Names.of(param)));
        }
        return Doc.concat(modifiers, recursor.scan(param.getType()), new Doc.Text(" " + Names.of(param)));
    }
}
