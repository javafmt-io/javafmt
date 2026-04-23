package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.AssertTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;

import java.util.List;

import org.jspecify.annotations.Nullable;

final class SimpleStatementRenderers {

    static Doc renderAssert(final AssertTree node, final Recursor recursor) {
        final var cond = recursor.scanOrText(node.getCondition());
        if (node.getDetail() == null) {
            return new Doc.Concat(List.of(new Doc.Text("assert "), cond, new Doc.Text(";")));
        }
        return new Doc.Concat(List.of(
            new Doc.Text("assert "),
            cond,
            new Doc.Text(" : "),
            recursor.scanOrText(node.getDetail()),
            new Doc.Text(";")));
    }

    static Doc renderSynchronized(final SynchronizedTree node, final Recursor recursor) {
        final var inner = node.getExpression() instanceof com.sun.source.tree.ParenthesizedTree pt
            ? pt.getExpression()
            : node.getExpression();
        final var header = new Doc.Concat(List.of(
            new Doc.Text("synchronized ("),
            recursor.scanOrText(inner),
            new Doc.Text(")")));
        final var stmts = BlockRenderer.blockStmts(node.getBlock(), recursor);
        return BlockRenderer.buildBlock(header, stmts, List.of());
    }

    static Doc renderLabeled(final LabeledStatementTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            new Doc.Text(node.getLabel() + ":"),
            new Doc.HardLine(),
            recursor.scanOrText(node.getStatement())));
    }

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

    static Doc renderExpressionStatement(final ExpressionStatementTree node, final Recursor recursor) {
        final var exprDoc = recursor.scan(node.getExpression());
        if (exprDoc != null) {
            return new Doc.Concat(List.of(exprDoc, new Doc.Text(";")));
        }
        return new Doc.Text(node.getExpression() + ";");
    }

    static Doc renderThrow(final ThrowTree node) {
        return new Doc.Text("throw " + node.getExpression() + ";");
    }

    private SimpleStatementRenderers() {}
}
