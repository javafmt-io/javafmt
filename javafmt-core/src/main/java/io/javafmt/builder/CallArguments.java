package io.javafmt.builder;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
final class CallArguments {

    Rendered render(final Tree call, final List<? extends ExpressionTree> arguments, final boolean lastCall, final Recursor recursor) {
        if (arguments.isEmpty()) {
            final var empty = emptyArguments(recursor.interior(call));
            return new Rendered(empty, empty, false);
        }
        final var trailing = IntStream.range(0, arguments.size())
                .mapToObj(i -> i + 1 < arguments.size() ? recursor.trailing(arguments.get(i)) : List.<CommentToken>of())
                .toList();
        final var lineCommentAfter = IntStream.range(0, arguments.size())
                .mapToObj(i -> CommentDocs.endsTheLine(trailing.get(i))
                    || (i + 1 < arguments.size() && CommentDocs.endsTheLine(recursor.pastTheSeparatorBefore(arguments.get(i + 1)))))
                .toList();
        final var inline = IntStream.range(0, arguments.size())
                .mapToObj(i -> lineCommentAfter.get(i) ? List.<CommentToken>of() : trailing.get(i))
                .toList();
        final var pastTheSeparator = IntStream.range(0, arguments.size())
                .mapToObj(i -> i == 0
                        ? recursor.pastTheSeparatorBefore(arguments.getFirst())
                        : Stream.concat(
                                        lineCommentAfter.get(i - 1)
                                                ? trailing.get(i - 1).stream()
                                                : Stream.<CommentToken>empty(),
                                        recursor.pastTheSeparatorBefore(arguments.get(i)).stream())
                                .toList())
                .toList();
        final var others = IntStream.range(0, arguments.size() - 1)
                .mapToObj(i -> withInlineComments(i, inline, renderArgument(arguments, i, trailing, recursor)))
                .toList();
        final var chain = lastCall && arguments.getLast() instanceof MethodInvocationTree last
                ? Optional.of(recursor.scanChain(last))
                : Optional.<MethodChainRenderer.ChainDocs>empty();
        final var lastDoc = chain.map(MethodChainRenderer.ChainDocs::doc).orElseGet(() -> recursor.scan(arguments.getLast()));
        final var rendered = Stream.concat(others.stream(), Stream.of(withInlineComments(arguments.size() - 1, inline, lastDoc)))
                .toList();
        final var closing = Doc.endsWithLineComment(rendered.getLast()) ? Doc.concat(Doc.HARD_LINE, new Doc.Text(")")) : new Doc.Text(")");
        final var packed = new Doc.Group(
                Doc.concat(
                        new Doc.Text("("),
                        Doc.continuationIndent(Doc.concat(Doc.SOFT_LINE, oneLineOrOnePerLine(rendered, pastTheSeparator))),
                        closing));
        final var firstOnTheCallLine = pastTheSeparator.getFirst().isEmpty() && !recursor.hasLeadingComments(arguments.getFirst())
                ? firstOnTheCallLine(rendered, pastTheSeparator, closing)
                : packed;
        if (isBlockLambda(arguments.getLast()) && hugsLastArgument(arguments, rendered, pastTheSeparator)) {
            final var hugged = onTheCallLine(others, rendered.getLast());
            return new Rendered(
                    new Doc.Choice(hugged, packed, Doc.Choice.Fit.OPENING_LINE),
                    new Doc.Choice(hugged, firstOnTheCallLine, Doc.Choice.Fit.OPENING_LINE),
                    true);
        }
        final var callLineFit = others.isEmpty() ? Doc.Choice.Fit.FIRST_BREAK : Doc.Choice.Fit.FIRST_BREAK_WITHOUT_SPLITTING_A_CHAIN;
        if (isExpressionLambda(arguments.getLast()) && hugsLastArgument(arguments, rendered, pastTheSeparator)) {
            final var hugged = onTheCallLine(flattenedEach(others), rendered.getLast());
            return new Rendered(
                    new Doc.Choice(hugged, onTheNextLine(rendered, pastTheSeparator, closing), callLineFit),
                    new Doc.Choice(hugged, firstOnTheCallLine, Doc.Choice.Fit.OPENING_LINE),
                    false);
        }
        if (arguments.getLast() instanceof ConditionalExpressionTree ternary
            && startsOnTheCallLine(ternary, others, pastTheSeparator, trailing, recursor)) {
            final var onTheCallLine = onTheCallLine(flattenedEach(others), rendered.getLast());
            final var hugged = new Doc.Choice(onTheCallLine, onTheNextLine(rendered, pastTheSeparator, closing), callLineFit);
            if (!printsFlat(rendered.getLast())) {
                return new Rendered(hugged, new Doc.Choice(onTheCallLine, firstOnTheCallLine, callLineFit), false);
            }
            return new Rendered(
                    new Doc.Group(
                            new Doc.IfBreak(
                                    new Doc.Choice(onTheNextLine(rendered, pastTheSeparator, closing), hugged, Doc.Choice.Fit.EVERY_LINE), packed)),
                    firstOnTheCallLine,
                    false);
        }
        final var trailingChain = chain.filter(docs -> docs.brokenAfterHead().isPresent()
            && startsOnTheCallLine(arguments.getLast(), others, pastTheSeparator, trailing, recursor));
        if (trailingChain.isEmpty()) {
            return new Rendered(packed, firstOnTheCallLine, false);
        }
        final var alwaysBroken = Doc.flatWidthOrBreak(trailingChain.get().doc()) < 0;
        final var onTheCallLine = onTheCallLine(
                others, trailingChain.get().brokenAfterHead().orElseThrow());
        final var hugged = new Doc.Choice(onTheCallLine, packed, Doc.Choice.Fit.OPENING_LINE);
        final var onePerLineBelow = Doc.concatOf(
                Stream.of(
                        Stream.of(new Doc.Text("(")),
                        Stream.of(
                                Doc.continuationIndent(
                                        Doc.concatOf(Stream.of(Stream.of(Doc.HARD_LINE), Doc.intersperse(Doc.HARD_LINE, rendered.stream()))))),
                        Stream.of(closing)));
        return new Rendered(
                alwaysBroken
                        ? hugged
                        : new Doc.Group(
                                new Doc.IfBreak(
                                        new Doc.Choice(
                                                onTheNextLine(rendered, pastTheSeparator, closing),
                                                new Doc.Choice(onePerLineBelow, hugged, Doc.Choice.Fit.EVERY_LINE),
                                                Doc.Choice.Fit.EVERY_LINE),
                                        packed)),
                alwaysBroken && !others.isEmpty()
                        ? new Doc.Choice(onTheCallLine, firstOnTheCallLine, Doc.Choice.Fit.OPENING_LINE)
                        : firstOnTheCallLine,
                false);
    }

    private Doc withInlineComments(final int index, final List<List<CommentToken>> inline, final Doc argument) {
        return CommentDocs.prepend(index == 0 ? List.of() : inline.get(index - 1), argument);
    }

    private Doc onTheCallLine(final List<Doc> others, final Doc last) {
        return Doc.concatOf(
                Stream.of(
                        Stream.of(new Doc.Text("(")),
                        Doc.intersperse(new Doc.Text(" "), Stream.concat(others.stream(), Stream.of(last))),
                        Stream.of(new Doc.Text(")"))));
    }

    private Doc onTheNextLine(final List<Doc> rendered, final List<List<CommentToken>> pastTheSeparator, final Doc closing) {
        return Doc.concat(
                new Doc.Text("("), Doc.continuationIndent(Doc.concat(Doc.HARD_LINE, onePerLineUnlessFlat(rendered, pastTheSeparator))), closing);
    }

    private Doc oneLineOrOnePerLine(final List<Doc> rendered, final List<List<CommentToken>> pastTheSeparator) {
        return new Doc.IfBreak(onePerLineUnlessFlat(rendered, pastTheSeparator), CommentDocs.commaSeparated(rendered, pastTheSeparator));
    }

    private Doc onePerLineUnlessFlat(final List<Doc> rendered, final List<List<CommentToken>> pastTheSeparator) {
        return new Doc.Group(
                CommentDocs.commaSeparated(rendered, pastTheSeparator),
                !rendered.stream()
                        .allMatch(CallArguments::printsFlat));
    }

    private Doc firstOnTheCallLine(final List<Doc> rendered, final List<List<CommentToken>> pastTheSeparator, final Doc closing) {
        if (rendered.size() == 1) {
            return Doc.concat(new Doc.Text("("), rendered.getFirst(), closing);
        }
        final var rest = Doc.concat(
                CommentDocs.afterComma(rendered.getFirst(), pastTheSeparator.get(1)),
                oneLineOrOnePerLine(rendered.subList(1, rendered.size()), pastTheSeparator.subList(1, pastTheSeparator.size())));
        return new Doc.Group(Doc.concat(new Doc.Text("("), rendered.getFirst(), Doc.continuationIndent(rest), closing));
    }

    private boolean startsOnTheCallLine(final ExpressionTree last,
                                        final List<Doc> others,
                                        final List<List<CommentToken>> pastTheSeparator,
                                        final List<List<CommentToken>> trailing,
                                        final Recursor recursor) {
        return others.stream()
                .allMatch(doc -> Doc.flatWidthOrBreak(doc) >= 0)
            && pastTheSeparator.stream()
                    .allMatch(List::isEmpty)
            && trailing.stream()
                    .allMatch(List::isEmpty)
            && !recursor.hasLeadingComments(last)
            && recursor.trailing(last).isEmpty();
    }

    private Doc renderArgument(final List<? extends ExpressionTree> arguments,
                               final int index,
                               final List<List<CommentToken>> trailing,
                               final Recursor recursor) {
        final UnaryOperator<Doc> withComma = d -> Doc.concat(d, new Doc.Text(","));
        return trailing.get(index).isEmpty()
                ? recursor.scanBeforeComments(arguments.get(index), withComma)
                : recursor.scanLeavingTrailing(arguments.get(index), withComma);
    }

    private Doc emptyArguments(final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return new Doc.Text("()");
        }
        final var inside = new Doc.Concat(
                Doc.intersperse(new Doc.Text(" "), comments.stream()
                        .map(CommentDocs::renderComment)));
        return Doc.concat(new Doc.Text(Doc.endsWithLineComment(inside) ? "( " : "("), CommentDocs.closeAfter(inside, List.of(), ")"));
    }

    private boolean hugsLastArgument(final List<? extends ExpressionTree> arguments,
                                     final List<Doc> rendered,
                                     final List<List<CommentToken>> pastTheSeparator) {
        final var others = rendered.subList(0, rendered.size() - 1);
        return arguments.stream()
                .limit(others.size())
                .noneMatch(CallArguments::isBlockLambda)
            && others.stream()
                    .allMatch(doc -> Doc.flatWidthOrBreak(doc) >= 0)
            && pastTheSeparator.stream()
                    .allMatch(List::isEmpty)
            && !Doc.endsWithLineComment(rendered.getLast())
            && !CommentDocs.commentBeforeFirstBreak(new Doc.Concat(rendered));
    }

    private boolean isBlockLambda(final Tree argument) {
        return argument instanceof LambdaExpressionTree lambda && lambda.getBodyKind() == LambdaExpressionTree.BodyKind.STATEMENT;
    }

    private boolean isExpressionLambda(final Tree argument) {
        return argument instanceof LambdaExpressionTree lambda && lambda.getBodyKind() == LambdaExpressionTree.BodyKind.EXPRESSION;
    }

    private boolean printsFlat(final Doc doc) {
        final var pending = new ArrayDeque<Doc>();
        pending.push(doc);
        while (!pending.isEmpty()) {
            switch (pending.pop()) {
                case Doc.HardLine _, Doc.LineStart _, Doc.BlockComment _ -> {
                    return false;
                }
                case Doc.Group(var contents, var preferBreak) -> {
                    if (preferBreak) {
                        return false;
                    }
                    pending.push(contents);
                }
                case Doc.Verbatim(var text, var _) -> {
                    if (text.indexOf('\n') >= 0) {
                        return false;
                    }
                }
                case Doc.Text(var text) -> {
                    if (text.stripLeading().startsWith("//")
                        || text.stripLeading().startsWith("/*")) {
                        return false;
                    }
                }
                case Doc.Line _, Doc.SoftLine _ -> {}
                case Doc.Indent(var contents) -> pending.push(contents);
                case Doc.Align(var contents) -> pending.push(contents);
                case Doc.IfBreak(var _, var flatContents) -> pending.push(flatContents);
                case Doc.Choice(var preferred, var _, var _, var _) -> pending.push(preferred);
                case Doc.Concat(var parts) -> parts.forEach(pending::push);
            }
        }
        return true;
    }

    private List<Doc> flattenedEach(final List<Doc> docs) {
        return docs.stream()
                .map(CallArguments::flattened)
                .toList();
    }

    private Doc flattened(final Doc doc) {
        return switch (doc) {
            case Doc.Line _ -> new Doc.Text(" ");
            case Doc.SoftLine _ -> new Doc.Text("");
            case Doc.Group(var contents, var _) -> flattened(contents);
            case Doc.IfBreak(var _, var flatContents) -> flattened(flatContents);
            case Doc.Choice(var preferred, var fallback, var fit, var chainWidth) when chainWidth >= 0 ->
                new Doc.Choice(flattened(preferred), fallback, fit, chainWidth);
            case Doc.Choice(var preferred, var _, var _, var _) -> flattened(preferred);
            case Doc.Concat(var parts) -> new Doc.Concat(parts.stream()
                    .map(CallArguments::flattened)
                    .toList());
            case Doc.Indent(var contents) -> new Doc.Indent(flattened(contents));
            case Doc.Align(var contents) -> new Doc.Align(flattened(contents));
            case Doc.Text _, Doc.Verbatim _, Doc.BlockComment _, Doc.HardLine _, Doc.LineStart _ -> doc;
        };
    }

    record Rendered(Doc doc, Doc onItsOwnLine, boolean hugsLastArgument) {}
}
