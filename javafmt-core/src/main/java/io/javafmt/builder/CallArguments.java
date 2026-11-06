package io.javafmt.builder;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
final class CallArguments {

    Rendered render(final Tree call, final List<? extends ExpressionTree> arguments, final Recursor recursor) {
        if (arguments.isEmpty()) {
            return new Rendered(emptyArguments(CommentDocs.inside(call, recursor)), false);
        }
        final var trailing = IntStream.range(0, arguments.size())
                .mapToObj(i -> i + 1 < arguments.size() ? recursor.trailing(arguments.get(i)) : List.<CommentToken>of())
                .toList();
        final var lineCommentAfter = IntStream.range(0, arguments.size())
                .mapToObj(
                        i -> CommentDocs.hasLineComment(trailing.get(i))
                            || (i + 1 < arguments.size() && CommentDocs.hasLineComment(recursor.pastTheSeparatorBefore(arguments.get(i + 1)))))
                .toList();
        final var inline = IntStream.range(0, arguments.size())
                .mapToObj(i -> lineCommentAfter.get(i) ? List.<CommentToken>of() : trailing.get(i))
                .toList();
        final var pastTheSeparator = IntStream.range(0, arguments.size())
                .mapToObj(
                        i -> i == 0
                                ? recursor.pastTheSeparatorBefore(arguments.getFirst())
                                : Stream.concat(
                                        lineCommentAfter.get(i - 1) ? trailing.get(i - 1).stream() : Stream.<CommentToken>empty(),
                                        recursor.pastTheSeparatorBefore(arguments.get(i)).stream())
                                        .toList())
                .toList();
        final var rendered = IntStream.range(0, arguments.size())
                .mapToObj(
                        i -> CommentDocs.prepend(List.of(), i == 0 ? List.of() : inline.get(i - 1), renderArgument(arguments, i, trailing, recursor)))
                .toList();
        final var closing = Doc.endsWithLineComment(rendered.getLast()) ? Doc.concat(Doc.HARD_LINE, new Doc.Text(")")) : new Doc.Text(")");
        final var packed = new Doc.Group(
                Doc.concat(
                        new Doc.Text("("), Doc.continuationIndent(Doc.concat(Doc.SOFT_LINE, CommentDocs.commaFill(rendered, pastTheSeparator))),
                        closing));
        if (!hugsLastArgument(arguments, rendered, pastTheSeparator)) {
            return new Rendered(packed, false);
        }
        final var hugged = Doc.concatOf(
                Stream.of(Stream.of(new Doc.Text("(")), Doc.intersperse(new Doc.Text(" "), rendered.stream()), Stream.of(new Doc.Text(")"))));
        return new Rendered(new Doc.Choice(hugged, packed, Doc.Choice.Fit.OPENING_LINE), true);
    }

    private Doc renderArgument(final List<? extends ExpressionTree> arguments,
                               final int index,
                               final List<List<CommentToken>> trailing,
                               final Recursor recursor) {
        if (index + 1 == arguments.size()) {
            return recursor.scan(arguments.get(index));
        }
        final UnaryOperator<Doc> withComma = d -> Doc.concat(d, new Doc.Text(","));
        return trailing.get(index).isEmpty()
                ? recursor.scanBeforeComments(arguments.get(index), withComma)
                : recursor.scanLeavingTrailing(arguments.get(index), withComma);
    }

    private Doc emptyArguments(final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return new Doc.Text("()");
        }
        final var inside = new Doc.Concat(Doc.intersperse(new Doc.Text(" "), comments.stream().map(CommentDocs::renderComment)));
        return Doc.endsWithLineComment(inside)
                ? Doc.concat(new Doc.Text("( "), inside, Doc.HARD_LINE, new Doc.Text(")"))
                : Doc.concat(new Doc.Text("("), inside, new Doc.Text(")"));
    }

    private boolean hugsLastArgument(final List<? extends ExpressionTree> arguments,
                                     final List<Doc> rendered,
                                     final List<List<CommentToken>> pastTheSeparator) {
        final var others = rendered.subList(0, rendered.size() - 1);
        return isBlockLambda(arguments.getLast())
            && arguments.stream().limit(others.size()).noneMatch(CallArguments::isBlockLambda)
            && others.stream().allMatch(doc -> Doc.flatWidthOrBreak(doc) >= 0)
            && pastTheSeparator.stream().allMatch(List::isEmpty)
            && !Doc.endsWithLineComment(rendered.getLast())
            && !CommentDocs.commentBeforeFirstBreak(new Doc.Concat(rendered));
    }

    private boolean isBlockLambda(final Tree argument) {
        return argument instanceof LambdaExpressionTree lambda && lambda.getBodyKind() == LambdaExpressionTree.BodyKind.STATEMENT;
    }

    record Rendered(Doc doc, boolean hugsLastArgument) {}
}
