package io.javafmt.builder;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
final class CallArguments {
    Rendered render(final Tree call, final List<? extends ExpressionTree> arguments, final Recursor recursor) {
        if (arguments.isEmpty()) {
            return new Rendered(emptyArguments(CommentDocs.inside(call, recursor)), false);
        }
        final var rendered = IntStream.range(0, arguments.size())
                .mapToObj(
                        i -> i + 1 < arguments.size()
                                ? recursor.scanBeforeComments(arguments.get(i), d -> Doc.concat(d, new Doc.Text(",")))
                                : recursor.scan(arguments.get(i)))
                .toList();
        final var packed = new Doc.Group(
                Doc.concat(
                        new Doc.Text("("), Doc.continuationIndent(Doc.concat(Doc.SOFT_LINE, CommentDocs.commaFill(arguments, rendered, recursor))),
                        new Doc.Text(")")));
        if (!hugsLastArgument(arguments, rendered, recursor)) {
            return new Rendered(packed, false);
        }
        final var hugged = Doc.concatOf(
                Stream.of(Stream.of(new Doc.Text("(")), Doc.intersperse(new Doc.Text(" "), rendered.stream()), Stream.of(new Doc.Text(")"))));
        return new Rendered(new Doc.Choice(hugged, packed, Doc.Choice.Fit.OPENING_LINE), true);
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

    private boolean hugsLastArgument(final List<? extends ExpressionTree> arguments, final List<Doc> rendered, final Recursor recursor) {
        final var others = rendered.subList(0, rendered.size() - 1);
        return isBlockLambda(arguments.getLast())
            && arguments.stream().limit(others.size()).noneMatch(CallArguments::isBlockLambda)
            && others.stream().allMatch(doc -> Doc.flatWidthOrBreak(doc) >= 0)
            && arguments.stream().allMatch(argument -> recursor.pastTheSeparatorBefore(argument).isEmpty())
            && !CommentDocs.commentBeforeFirstBreak(new Doc.Concat(rendered));
    }

    private boolean isBlockLambda(final Tree argument) {
        return argument instanceof LambdaExpressionTree lambda && lambda.getBodyKind() == LambdaExpressionTree.BodyKind.STATEMENT;
    }

    record Rendered(Doc doc, boolean hugsLastArgument) {}
}
