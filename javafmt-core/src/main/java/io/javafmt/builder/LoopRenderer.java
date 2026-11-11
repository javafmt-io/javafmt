package io.javafmt.builder;

import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
final class LoopRenderer {

    Doc renderFor(final ForLoopTree node, final Recursor recursor) {
        final var header = buildForHeader(node, recursor);
        return BlockRenderer.braced(header, node.getStatement(), recursor);
    }

    private Doc buildForHeader(final ForLoopTree node, final Recursor recursor) {
        final var initializers = node.getInitializer().isEmpty()
                ? Stream.<Doc>of(new Doc.Text(";"))
                : forInitializers(node.getInitializer(), recursor);
        final var condition = node.getCondition() == null
                ? new Doc.Text(";")
                : CommentDocs.withSeparator(node.getCondition(), UnaryOperator.identity(), ";", recursor);
        final var pieces = Stream.of(
                        Stream.<Doc>of(new Doc.Text("for (")),
                        initializers,
                        Stream.of(new Doc.Text(" "), condition),
                        node.getUpdate().isEmpty() ? Stream.<Doc>empty() : Stream.<Doc>of(new Doc.Text(" ")),
                        commaSeparated(node.getUpdate(), "", recursor))
                .flatMap(Function.identity())
                .toList();
        return CommentDocs.closeAfter(Doc.continuationIndent(joinedOnOneLine(pieces)), recursor.tail(node), ")");
    }

    private Doc joinedOnOneLine(final List<Doc> pieces) {
        return pieces.stream()
                .reduce((
                        joined,
                        piece) -> Doc.endsWithLineComment(joined)
                        ? Doc.concat(joined, Doc.HARD_LINE, withoutLeadingSpace(piece))
                        : Doc.concat(joined, piece))
                .orElseThrow();
    }

    private Doc withoutLeadingSpace(final Doc piece) {
        return piece instanceof Doc.Text(var text) ? new Doc.Text(text.stripLeading()) : piece;
    }

    /**
     * The variables of a {@code for} header. Java declares them all under one type — there is no
     * way to write two types in a single {@code for} initializer — but javac hands back a complete
     * {@link VariableTree} for each, so rendering every one in full writes
     * {@code for (int i = 0, int length = n; …)}. Only the first carries the modifiers and the type;
     * the rest are name and initializer.
     *
     * <p>The C-style form {@code for (int i = 0, a[] = null; …)} is the exception: there the
     * variables really do have different types, and sharing the first one's would drop the extra
     * dimensions. That falls back to the plain rendering, which the output verifier refuses rather
     * than writing something wrong.
     */
    private Stream<Doc> forInitializers(final List<? extends StatementTree> initializers, final Recursor recursor) {
        if (!sharesOneDeclaration(initializers)) {
            return commaSeparated(initializers, ";", recursor);
        }
        final var count = initializers.size();
        return spaced(IntStream.range(0, count)
                .mapToObj(i -> i == 0
                        ? CommentDocs.withSeparator(initializers.getFirst(), BlockRenderer::stripTrailingSemicolonDoc, ",", recursor)
                        : continuation((VariableTree) initializers.get(i), i + 1 < count ? "," : ";", recursor)));
    }

    private Doc continuation(final VariableTree variable, final String separator, final Recursor recursor) {
        return CommentDocs.attachedWithSeparator(variable, continuationOf(variable, recursor), separator, recursor);
    }

    private boolean sharesOneDeclaration(final List<? extends StatementTree> initializers) {
        if (initializers.size() < 2 || !(initializers.getFirst() instanceof VariableTree first)) {
            return false;
        }
        return initializers.stream()
                .allMatch(t -> t instanceof VariableTree v && v.getType() == first.getType());
    }

    private Doc continuationOf(final VariableTree variable, final Recursor recursor) {
        final var name = new Doc.Text(Names.of(variable));
        final var initializer = variable.getInitializer();
        return initializer == null ? name : Doc.concat(name, new Doc.Text(" = "), recursor.scan(initializer));
    }

    private Stream<Doc> commaSeparated(final List<? extends Tree> trees, final String afterTheLast, final Recursor recursor) {
        return spaced(IntStream.range(0, trees.size())
                .mapToObj(i -> CommentDocs.withSeparator(
                        trees.get(i), BlockRenderer::stripTrailingSemicolonDoc, i + 1 < trees.size() ? "," : afterTheLast, recursor)));
    }

    private Stream<Doc> spaced(final Stream<Doc> elements) {
        return Doc.intersperse(new Doc.Text(" "), elements);
    }

    Doc renderEnhancedFor(final EnhancedForLoopTree node, final Recursor recursor) {
        final var variable = node.getVariable();
        final var typeDoc = variable.getType() == null ? new Doc.Text("var") : recursor.scan(variable.getType());
        final var header = Doc.concat(
                new Doc.Text("for ("),
                ModifierRenderer.prefixOf(variable.getModifiers(), recursor),
                typeDoc,
                new Doc.Text(" " + Names.of(variable) + " : "),
                recursor.scan(node.getExpression()),
                new Doc.Text(")"));
        return BlockRenderer.braced(header, node.getStatement(), recursor);
    }

    Doc renderWhile(final WhileLoopTree node, final Recursor recursor) {
        final var header = Doc.concat(new Doc.Text("while "), recursor.scan(node.getCondition()));
        return BlockRenderer.braced(header, node.getStatement(), recursor);
    }

    Doc renderDoWhile(final DoWhileLoopTree node, final Recursor recursor) {
        final var statements = BlockRenderer.blockStatements(node.getStatement(), recursor);
        return Doc.concatOf(
                Stream.of(
                        BlockRenderer.openedWith(new Doc.Text("do"), node.getStatement(), statements, recursor),
                        Stream.of(Doc.HARD_LINE, new Doc.Text("} while "), recursor.scan(node.getCondition()), new Doc.Text(";"))));
    }
}
