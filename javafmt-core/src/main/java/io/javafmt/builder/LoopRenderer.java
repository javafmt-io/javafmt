package io.javafmt.builder;

import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class LoopRenderer {

    Doc renderFor(final ForLoopTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var header = buildForHeader(node, recursor);
        return BlockRenderer.buildBlock(header, BlockRenderer.blockStmts(node.getStatement(), recursor, attacher), List.of());
    }

    private Doc buildForHeader(final ForLoopTree node, final Recursor recursor) {
        return Doc.concatOf(
                Stream.of(
                        Stream.of(new Doc.Text("for (")),
                        forInitializers(node.getInitializer(), recursor),
                        Stream.of(new Doc.Text("; ")),
                        node.getCondition() != null ? Stream.of(renderPart(node.getCondition(), recursor, false)) : Stream.<Doc>empty(),
                        Stream.of(new Doc.Text(node.getUpdate().isEmpty() ? ";" : "; ")),
                        joinInto(node.getUpdate(), recursor, true),
                        Stream.of(new Doc.Text(")"))));
    }

    /**
     * The variables of a {@code for} header. Java declares them all under one type — there is no
     * way to write two types in a single {@code for} initializer — but javac hands back a complete
     * {@link VariableTree} for each, so rendering every one in full writes
     * {@code for (int i = 0, int len = n; …)}. Only the first carries the modifiers and the type;
     * the rest are name and initializer.
     *
     * <p>The C-style form {@code for (int i = 0, a[] = null; …)} is the exception: there the
     * variables really do have different types, and sharing the first one's would drop the extra
     * dimensions. That falls back to the plain rendering, which the output verifier refuses rather
     * than writing something wrong.
     */
    private Stream<Doc> forInitializers(final List<? extends StatementTree> initializers, final Recursor recursor) {
        if (!sharesOneDeclaration(initializers)) {
            return joinInto(initializers, recursor, true);
        }
        final var continuations = initializers.stream()
                .skip(1)
                .map(VariableTree.class::cast)
                .map(v -> continuationOf(v, recursor));
        return Doc.intersperse(new Doc.Text(", "), Stream.concat(Stream.of(renderPart(initializers.getFirst(), recursor, true)), continuations));
    }

    private boolean sharesOneDeclaration(final List<? extends StatementTree> initializers) {
        if (initializers.size() < 2 || !(initializers.getFirst() instanceof VariableTree first)) {
            return false;
        }
        return initializers.stream().allMatch(t -> t instanceof VariableTree v && v.getType() == first.getType());
    }

    private Doc continuationOf(final VariableTree variable, final Recursor recursor) {
        final var name = new Doc.Text(Names.of(variable));
        final var initializer = variable.getInitializer();
        return initializer == null ? name : new Doc.Concat(List.of(name, new Doc.Text(" = "), recursor.scan(initializer)));
    }

    private Stream<Doc> joinInto(final List<? extends Tree> trees, final Recursor recursor, final boolean stripSemicolon) {
        return Doc.intersperse(new Doc.Text(", "), trees.stream().map(t -> renderPart(t, recursor, stripSemicolon)));
    }

    private Doc renderPart(final Tree tree, final Recursor recursor, final boolean stripSemicolon) {
        final var doc = recursor.scan(tree);
        return stripSemicolon ? BlockRenderer.stripTrailingSemicolonDoc(doc) : doc;
    }

    Doc renderEnhancedFor(final EnhancedForLoopTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var variable = node.getVariable();
        final var typeDoc = variable.getType() == null ? new Doc.Text("var") : recursor.scan(variable.getType());
        final var header = new Doc.Concat(
                List.of(
                        new Doc.Text("for (" + ModifierRenderer.prefixOf(variable.getModifiers(), recursor)),
                        typeDoc,
                        new Doc.Text(" " + Names.of(variable) + " : "),
                        recursor.scan(node.getExpression()),
                        new Doc.Text(")")));
        return BlockRenderer.buildBlock(header, BlockRenderer.blockStmts(node.getStatement(), recursor, attacher), List.of());
    }

    Doc renderWhile(final WhileLoopTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var header = new Doc.Concat(List.of(new Doc.Text("while "), recursor.scan(node.getCondition())));
        return BlockRenderer.buildBlock(header, BlockRenderer.blockStmts(node.getStatement(), recursor, attacher), List.of());
    }

    Doc renderDoWhile(final DoWhileLoopTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var stmts = BlockRenderer.blockStmts(node.getStatement(), recursor, attacher);
        return new Doc.Concat(
                Stream.concat(
                        BlockRenderer.blockParts("do", stmts),
                        Stream.<Doc>of(Doc.HARD_LINE, new Doc.Text("} while "), recursor.scan(node.getCondition()), new Doc.Text(";"))));
    }
}
