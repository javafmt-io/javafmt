package io.javafmt.builder;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
final class MethodChainRenderer {

    private final Set<String> FIRST_LINE_ENDS = Set.of("stream", "parallelStream", "builder", "newBuilder", "toBuilder");

    Doc render(final MethodInvocationTree node, final Recursor recursor) {
        return chainDocs(node, recursor).doc();
    }

    ChainDocs chainDocs(final MethodInvocationTree node, final Recursor recursor) {
        final var calls = DocBuilder.walk(node, call -> call.getMethodSelect() instanceof MemberSelectTree ms
                    && ms.getExpression() instanceof MethodInvocationTree receiver
                        ? receiver
                        : null)
                .toList();
        final var links = calls.stream()
                .map(call -> call == node ? links(call, true, recursor) : withTrailingComments(links(call, false, recursor), recursor.trailing(call)))
                .toList()
                .reversed()
                .stream()
                .flatMap(List::stream)
                .toList();
        final var threeOrMoreCalls = calls.size() >= 3;
        final var chopped = choppedAfter(links);
        if (chopped.isPresent()) {
            final var afterHead = brokenAfter(links, chopped.getAsInt());
            final var beforeEveryCall = brokenAfter(links, 0);
            final var doc = chopped.getAsInt() == 0
                    ? afterHead
                    : Doc.Choice.unlessPastTheChainColumn(
                            new Doc.Choice(afterHead, beforeEveryCall, Doc.Choice.Fit.OPENING_LINE),
                            beforeEveryCall,
                            Doc.widthBeforeFirstBreak(joined(links.subList(0, chopped.getAsInt() + 1))));
            return new ChainDocs(doc, everyLinkStaysOnOneLine(links) ? Optional.of(afterHead) : Optional.empty());
        }
        if (calls.size() == 1) {
            return new ChainDocs(joined(links), Optional.empty());
        }
        final var headEnd = !threeOrMoreCalls
            && links.getLast().endsTheFirstLine()
            && !links.getFirst().endsTheLine()
                ? links.size() - 1
                : 0;
        final var group = headEnd == 0
                ? chainGroup(
                        Doc.concat(
                                aboveTheCalls(links.getFirst().doc()), Doc.continuationIndent(tail(links, 1, MethodChainRenderer::separatorAfter))))
                : new Doc.Group(
                        new Doc.IfBreak(
                                new Doc.Choice(brokenAfter(links, headEnd), brokenAfter(links, 0), Doc.Choice.Fit.OPENING_LINE), joined(links)),
                        Doc.flatWidthOrBreak(joined(links)) < 0);
        final var chainWidth = threeOrMoreCalls
                ? Doc.widthBeforeFirstBreak(joined(links))
                : headEnd == links.size() - 1 || recursor.isLoneLambdaBody(node)
                        ? -1
                        : Doc.flatWidthOrBreak(joined(links.subList(0, links.size() - 1)));
        final var broken = Doc.Choice.unlessPastTheChainColumn(group, group.contents(), chainWidth);
        if (!hugsLastLink(links)) {
            return new ChainDocs(broken, everyLinkStaysOnOneLine(links) ? Optional.of(brokenAfter(links, headEnd)) : Optional.empty());
        }
        return new ChainDocs(
                new Doc.Choice(Doc.Choice.unlessPastTheChainColumn(joined(links), group.contents(), chainWidth), broken, Doc.Choice.Fit.OPENING_LINE),
                Optional.empty());
    }

    private boolean everyLinkStaysOnOneLine(final List<Link> links) {
        return links.stream()
                .allMatch(link -> !link.endsTheLine() && !link.endsWithComment() && Doc.flatWidthOrBreak(link.doc()) >= 0);
    }

    private OptionalInt choppedAfter(final List<Link> links) {
        final var head = IntStream.range(0, links.size() - 1)
                .filter(i -> links.get(i).endsTheFirstLine())
                .findFirst();
        return head.isPresent()
            && links.subList(0, head.getAsInt()).stream()
                    .noneMatch(Link::endsTheLine)
                ? head
                : OptionalInt.empty();
    }

    private boolean hugsLastLink(final List<Link> links) {
        return links.getLast().hugsLastArgument()
            && links.subList(0, links.size() - 1).stream()
                    .noneMatch(link -> link.endsTheLine() || Doc.flatWidthOrBreak(link.doc()) < 0);
    }

    Doc.Group chainGroup(final Doc contents) {
        return new Doc.Group(contents, Doc.flatWidthOrBreak(contents) < 0);
    }

    private Doc brokenAfter(final List<Link> links, final int headEnd) {
        return Doc.concat(
                aboveTheCalls(new Doc.Group(joined(links.subList(0, headEnd + 1)))),
                Doc.continuationIndent(tail(links, headEnd + 1, _ -> Doc.HARD_LINE)));
    }

    private Doc aboveTheCalls(final Doc head) {
        return Doc.flatWidthOrBreak(head) < 0 ? head : Doc.continuationIndent(head);
    }

    private Doc tail(final List<Link> links, final int from, final Function<Link, Doc> separator) {
        return new Doc.Concat(IntStream.range(from, links.size())
                .boxed()
                .flatMap(i -> Stream.of(
                        separator.apply(links.get(i - 1)), links.get(i).onItsOwnLine()))
                .toList());
    }

    private Doc separatorAfter(final Link link) {
        if (link.endsTheLine()) {
            return Doc.HARD_LINE;
        }
        return link.endsWithComment() ? Doc.LINE : Doc.SOFT_LINE;
    }

    private Doc joined(final List<Link> links) {
        return new Doc.Concat(IntStream.range(0, links.size())
                .boxed()
                .flatMap(i -> i > 0 && links.get(i - 1).endsWithComment()
                        ? Stream.of(new Doc.Text(" "), links.get(i).doc())
                        : Stream.of(links.get(i).doc()))
                .toList());
    }

    private List<Link> links(final MethodInvocationTree call, final boolean lastCall, final Recursor recursor) {
        if (call.getMethodSelect() instanceof MemberSelectTree ms) {
            final var head = new Doc.Concat(
                    Stream.concat(
                            Stream.of(new Doc.Text(".")),
                            Stream.concat(
                                    typeArguments(call, recursor),
                                    Stream.of(new Doc.Text(ms.getIdentifier().toString())))));
            final var arguments = CallArguments.render(call, call.getArguments(), lastCall, recursor);
            final var callSuffix = Doc.concat(head, arguments.doc());
            final var onItsOwnLine = Doc.concat(head, arguments.onItsOwnLine());
            final var endsTheFirstLine = FIRST_LINE_ENDS.contains(
                    ms.getIdentifier().toString());
            if (ms.getExpression() instanceof MethodInvocationTree) {
                return List.of(new Link(callSuffix, onItsOwnLine, false, false, arguments.hugsLastArgument(), endsTheFirstLine));
            }
            final var receiver = recursor.scan(ms.getExpression());
            if (createsABuilder(ms.getExpression())) {
                final var commented = Doc.endsWithLineComment(receiver);
                return List.of(
                        new Link(receiver, receiver, commented, commented, false, true),
                        new Link(callSuffix, onItsOwnLine, false, false, arguments.hugsLastArgument(), endsTheFirstLine));
            }
            final var first = Doc.concat(receiver, callSuffix);
            return List.of(new Link(first, first, false, false, arguments.hugsLastArgument(), endsTheFirstLine));
        }
        final var head = new Doc.Concat(Stream.concat(typeArguments(call, recursor), Stream.of(recursor.scan(call.getMethodSelect()))));
        final var arguments = CallArguments.render(call, call.getArguments(), lastCall, recursor);
        final var first = Doc.concat(head, arguments.doc());
        return List.of(new Link(first, first, false, false, arguments.hugsLastArgument(), false));
    }

    private boolean createsABuilder(final ExpressionTree receiver) {
        return receiver instanceof NewClassTree created
            && created.getClassBody() == null
            && simpleName(created.getIdentifier()).endsWith("Builder");
    }

    private String simpleName(final Tree type) {
        return switch (type) {
            case ParameterizedTypeTree parameterized -> simpleName(parameterized.getType());
            case MemberSelectTree select -> select.getIdentifier().toString();
            case IdentifierTree identifier -> identifier.getName().toString();
            default -> "";
        };
    }

    private List<Link> withTrailingComments(final List<Link> links, final List<CommentToken> comments) {
        return Stream.concat(links.subList(0, links.size() - 1).stream(), Stream.of(withTrailingComments(links.getLast(), comments)))
                .toList();
    }

    /**
     * The link with {@code comments} hung off it. The comments belong to the link the author wrote
     * them beside; attaching them to the chain as a whole moved them above the statement, where
     * they read as annotating all of it.
     */
    private Link withTrailingComments(final Link link, final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return link;
        }
        return new Link(
                CommentDocs.appendTrailing(link.doc(), comments),
                CommentDocs.appendTrailing(link.onItsOwnLine(), comments),
                CommentDocs.hasLineComment(comments),
                true,
                false,
                link.endsTheFirstLine());
    }

    private Stream<Doc> typeArguments(final MethodInvocationTree mit, final Recursor recursor) {
        final var arguments = mit.getTypeArguments();
        if (arguments.isEmpty()) {
            return Stream.empty();
        }
        return Stream.concat(
                Stream.of(new Doc.Text("<")),
                Stream.concat(
                        Doc.intersperse(new Doc.Text(", "), arguments.stream()
                                .map(recursor::scan)),
                        Stream.of(new Doc.Text(">"))));
    }

    /**
     * One link of the chain. {@code endsTheLine} marks a link whose own trailing {@code //}
     * comment closes the line, so the link after it has to start a new one however the enclosing
     * group prints.
     */
    private record Link(Doc doc,
                        Doc onItsOwnLine,
                        boolean endsTheLine,
                        boolean endsWithComment,
                        boolean hugsLastArgument,
                        boolean endsTheFirstLine) {}

    record ChainDocs(Doc doc, Optional<Doc> brokenAfterHead) {}
}
