package io.javafmt.builder;

import com.sun.source.tree.AssertTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.YieldTree;
import io.javafmt.doc.Doc;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
final class SimpleStatementRenderers {

    Doc renderAssert(final AssertTree node, final Recursor recursor) {
        final var cond = recursor.scan(node.getCondition());
        if (node.getDetail() == null) {
            return Doc.concat(new Doc.Text("assert "), cond, new Doc.Text(";"));
        }
        return Doc.concat(new Doc.Text("assert "), cond, new Doc.Text(" : "), recursor.scan(node.getDetail()), new Doc.Text(";"));
    }

    Doc renderSynchronized(final SynchronizedTree node, final Recursor recursor) {
        final var inner = node.getExpression() instanceof com.sun.source.tree.ParenthesizedTree pt ? pt.getExpression() : node.getExpression();
        final var header = Doc.concat(new Doc.Text("synchronized ("), recursor.scan(inner), new Doc.Text(")"));
        final var stmts = BlockRenderer.blockStmts(node.getBlock(), recursor);
        return BlockRenderer.buildBlock(header, stmts, List.of());
    }

    Doc renderYield(final YieldTree node, final Recursor recursor) {
        return Doc.concat(new Doc.Text("yield "), recursor.scan(node.getValue()), new Doc.Text(";"));
    }

    Doc renderLabeled(final LabeledStatementTree node, final Recursor recursor) {
        return Doc.concat(new Doc.Text(node.getLabel() + ":"), Doc.HARD_LINE, recursor.scan(node.getStatement()));
    }

    Doc renderReturn(final ReturnTree node, final Recursor recursor) {
        if (node.getExpression() == null) {
            return new Doc.Text("return;");
        }
        return Doc.concat(new Doc.Text("return "), recursor.scan(node.getExpression()), new Doc.Text(";"));
    }

    Doc renderExpressionStatement(final ExpressionStatementTree node, final Recursor recursor) {
        return Doc.concat(recursor.scan(node.getExpression()), new Doc.Text(";"));
    }

    Doc renderThrow(final ThrowTree node, final Recursor recursor) {
        return Doc.concat(new Doc.Text("throw "), recursor.scan(node.getExpression()), new Doc.Text(";"));
    }
}
