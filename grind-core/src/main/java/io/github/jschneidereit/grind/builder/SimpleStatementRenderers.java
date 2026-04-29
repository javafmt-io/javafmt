package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.AssertTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.YieldTree;

import java.util.List;
import io.github.jschneidereit.grind.doc.Doc;

final class SimpleStatementRenderers {

    static Doc renderAssert(final AssertTree node, final Recursor recursor) {
        final var cond = recursor.scan(node.getCondition());
        if (node.getDetail() == null) {
            return new Doc.Concat(List.of(new Doc.Text("assert "), cond, new Doc.Text(";")));
        }
        return new Doc.Concat(List.of(
            new Doc.Text("assert "),
            cond,
            new Doc.Text(" : "),
            recursor.scan(node.getDetail()),
            new Doc.Text(";")));
    }

    static Doc renderSynchronized(final SynchronizedTree node, final Recursor recursor) {
        final var inner = node.getExpression() instanceof com.sun.source.tree.ParenthesizedTree pt
            ? pt.getExpression()
            : node.getExpression();
        final var header = new Doc.Concat(List.of(
            new Doc.Text("synchronized ("),
            recursor.scan(inner),
            new Doc.Text(")")));
        final var stmts = BlockRenderer.blockStmts(node.getBlock(), recursor);
        return BlockRenderer.buildBlock(header, stmts, List.of());
    }

    static Doc renderYield(final YieldTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            new Doc.Text("yield "),
            recursor.scan(node.getValue()),
            new Doc.Text(";")));
    }

    static Doc renderLabeled(final LabeledStatementTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            new Doc.Text(node.getLabel() + ":"),
            new Doc.HardLine(),
            recursor.scan(node.getStatement())));
    }

    static Doc renderReturn(final ReturnTree node, final Recursor recursor) {
        if (node.getExpression() == null) {
            return new Doc.Text("return;");
        }
        return new Doc.Concat(List.of(
            new Doc.Text("return "),
            recursor.scan(node.getExpression()),
            new Doc.Text(";")));
    }

    static Doc renderExpressionStatement(final ExpressionStatementTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(recursor.scan(node.getExpression()), new Doc.Text(";")));
    }

    static Doc renderThrow(final ThrowTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            new Doc.Text("throw "),
            recursor.scan(node.getExpression()),
            new Doc.Text(";")));
    }

    private SimpleStatementRenderers() {}
}
