package io.javafmt.builder;

import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import io.javafmt.doc.Doc;

final class LoopRenderer {

    static Doc renderFor(final ForLoopTree node, final Recursor recursor) {
        final var header = buildForHeader(node, recursor);
        return BlockRenderer.buildBlock(header, BlockRenderer.blockStmts(node.getStatement(), recursor), List.of());
    }

    private static Doc buildForHeader(final ForLoopTree node, final Recursor recursor) {
        final var parts = new ArrayList<Doc>();
        parts.add(new Doc.Text("for ("));
        joinInto(parts, node.getInitializer(), recursor, true);
        parts.add(new Doc.Text("; "));
        if (node.getCondition() != null) {
            parts.add(renderPart(node.getCondition(), recursor, false));
        }
        parts.add(new Doc.Text("; "));
        joinInto(parts, node.getUpdate(), recursor, true);
        parts.add(new Doc.Text(")"));
        return new Doc.Concat(parts);
    }

    private static void joinInto(
            final List<Doc> parts,
            final List<? extends Tree> trees,
            final Recursor recursor,
            final boolean stripSemicolon) {
        for (var i = 0; i < trees.size(); i++) {
            if (i > 0) {
                parts.add(new Doc.Text(", "));
            }
            parts.add(renderPart(trees.get(i), recursor, stripSemicolon));
        }
    }

    private static Doc renderPart(final Tree tree, final Recursor recursor, final boolean stripSemicolon) {
        final var doc = recursor.scan(tree);
        return stripSemicolon ? BlockRenderer.stripTrailingSemicolonDoc(doc) : doc;
    }

    static Doc renderEnhancedFor(final EnhancedForLoopTree node, final Recursor recursor) {
        final var variable = node.getVariable();
        final var typeDoc = variable.getType() == null ? new Doc.Text("var") : recursor.scan(variable.getType());
        final var header = new Doc.Concat(List.of(
            new Doc.Text("for ("),
            typeDoc,
            new Doc.Text(" " + variable.getName() + " : "),
            recursor.scan(node.getExpression()),
            new Doc.Text(")")
        ));
        return BlockRenderer.buildBlock(header, BlockRenderer.blockStmts(node.getStatement(), recursor), List.of());
    }

    static Doc renderWhile(final WhileLoopTree node, final Recursor recursor) {
        final var header = new Doc.Concat(List.of(
            new Doc.Text("while "),
            recursor.scan(node.getCondition())
        ));
        return BlockRenderer.buildBlock(header, BlockRenderer.blockStmts(node.getStatement(), recursor), List.of());
    }

    static Doc renderDoWhile(final DoWhileLoopTree node, final Recursor recursor) {
        final var stmts = BlockRenderer.blockStmts(node.getStatement(), recursor);
        return new Doc.Concat(Stream.concat(
            BlockRenderer.blockParts("do", stmts),
            Stream.<Doc>of(
                new Doc.HardLine(),
                new Doc.Text("} while "),
                recursor.scan(node.getCondition()),
                new Doc.Text(";"))
        ));
    }

    private LoopRenderer() {}
}
