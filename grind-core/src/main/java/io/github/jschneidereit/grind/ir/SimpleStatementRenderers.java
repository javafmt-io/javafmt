package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ReturnTree;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

final class SimpleStatementRenderers {

    static Doc renderReturn(final ReturnTree node, final Recursor recursor) {
        if (node.getExpression() == null) {
            return new Doc.Text("return;");
        }
        final @Nullable Doc exprDoc = recursor.scan(node.getExpression());
        if (exprDoc != null) {
            return new Doc.Concat(List.of(
                new Doc.Text("return "),
                exprDoc,
                new Doc.Text(";")
            ));
        }
        return new Doc.Text("return " + node.getExpression() + ";");
    }

    static Doc renderExpressionStatement(final ExpressionStatementTree node) {
        return new Doc.Text(node.getExpression() + ";");
    }

    private SimpleStatementRenderers() {}
}
