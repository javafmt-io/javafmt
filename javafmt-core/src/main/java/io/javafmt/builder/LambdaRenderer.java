package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentScanner;
import lombok.experimental.UtilityClass;

import java.util.Optional;
import java.util.stream.Stream;

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
        final var header = Doc.concat(paramList, new Doc.Text(" ->"));
        if (block.getStatements().size() != 1 || !writtenOnOneLine(node, block, recursor)) {
            return BlockRenderer.braced(header, block, recursor);
        }
        return Optional.ofNullable(recursor.scan(block.getStatements().getFirst()))
                .map(statement -> keptOnOneLine(header, statement))
                .orElseGet(() -> BlockRenderer.braced(header, block, recursor));
    }

    private Doc keptOnOneLine(final Doc header, final Doc statement) {
        final var braced = Doc.concatOf(Stream.of(Stream.of(header, new Doc.Text(" {")), Stream.of(Doc.indentedLine(statement)), Doc.closeBrace()));
        final var oneLine = Doc.concat(header, new Doc.Text(" {"), statement, new Doc.Text("}"));
        return Doc.flatWidthOrBreak(oneLine) < 0 ? braced : new Doc.Choice(new Doc.Group(oneLine), braced, Doc.Choice.Fit.OPENING_LINE);
    }

    private boolean writtenOnOneLine(final LambdaExpressionTree node, final BlockTree block, final Recursor recursor) {
        final var lambda = recursor.sourceOf(node);
        return recursor.sourceOf(block).lines().count() == 1 && CommentScanner.withoutComments(lambda).equals(lambda);
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
        if (params.size() == 1 && params.getFirst().getType() == null && recursor.sourceSpan(node).charAt(0) != '(') {
            return new Doc.Text(Names.of(params.getFirst()));
        }
        return WrappableList.of("(", ",", ")", params.stream().map(p -> renderParam(p, recursor)));
    }

    /**
     * One lambda parameter, modifiers and all. {@code (final String t) -> …} is legal and the
     * modifiers were never read, so the {@code final} was dropped and the file refused.
     */
    private Doc renderParam(final VariableTree param, final Recursor recursor) {
        final var modifiers = ModifierRenderer.prefixOf(param.getModifiers(), recursor);
        if (param.getType() == null && !declaredWithVar(param, recursor)) {
            return Doc.concat(modifiers, new Doc.Text(Names.of(param)));
        }
        return Doc.concat(modifiers, MethodRenderer.typeAndName(param, recursor));
    }

    private boolean declaredWithVar(final VariableTree param, final Recursor recursor) {
        final var span = recursor.sourceSpan(param);
        return span.length() > 3 && "var".contentEquals(span.subSequence(0, 3)) && !Character.isJavaIdentifierPart(span.charAt(3));
    }
}
