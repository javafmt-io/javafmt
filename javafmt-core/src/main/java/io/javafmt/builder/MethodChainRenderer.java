package io.javafmt.builder;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.Pairs;
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
                        Doc.cannotRenderFlat(joined(links)));
        final var chainWidth = chainWidthOf(node, links, threeOrMoreCalls, headEnd, recursor);
        final var broken = Doc.Choice.unlessPastTheChainColumn(group, group.contents(), chainWidth);
        if (!hugsLastLink(links)) {
            return new ChainDocs(broken, everyLinkStaysOnOneLine(links) ? Optional.of(brokenAfter(links, headEnd)) : Optional.empty());
        }
        return new ChainDocs(
                new Doc.Choice(Doc.Choice.unlessPastTheChainColumn(joined(links), group.contents(), chainWidth), broken, Doc.Choice.Fit.OPENING_LINE),
                Optional.empty());
    }

    private int chainWidthOf(final Tree node, final List<Link> links, final boolean threeOrMoreCalls, final int headEnd, final Recursor recursor) {
        if (threeOrMoreCalls) {
            return Doc.widthBeforeFirstBreak(joined(links));
        }
        if (headEnd == links.size() - 1 || recursor.facts().isLoneLambdaBody(node)) {
            return -1;
        }
        return Doc.flatWidthOrBreak(joined(links.subList(0, links.size() - 1)));
    }

    private boolean everyLinkStaysOnOneLine(final List<Link> links) {
        return links.stream()
                .allMatch(link -> !link.endsTheLine() && Doc.canRenderFlat(link.doc()));
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
                    .noneMatch(link -> link.endsTheLine() || Doc.cannotRenderFlat(link.doc()));
    }

    Doc.Group chainGroup(final Doc contents) {
        return new Doc.Group(contents, Doc.cannotRenderFlat(contents));
    }

    private Doc brokenAfter(final List<Link> links, final int headEnd) {
        return Doc.concat(
                aboveTheCalls(new Doc.Group(joined(links.subList(0, headEnd + 1)))),
                Doc.continuationIndent(tail(links, headEnd + 1, _ -> Doc.HARD_LINE)));
    }

    private Doc aboveTheCalls(final Doc head) {
        return Doc.cannotRenderFlat(head) ? head : Doc.continuationIndent(head);
    }

    private Doc tail(final List<Link> links, final int from, final Function<Link, Doc> separator) {
        return new Doc.Concat(links.subList(from - 1, links.size()).stream()
                .gather(Pairs.<Link, Stream<Doc>>pairwise((previous, link) -> Stream.of(separator.apply(previous), link.onItsOwnLine())))
                .flatMap(Function.identity())
                .toList());
    }

    private Doc separatorAfter(final Link link) {
        return link.endsTheLine() ? Doc.HARD_LINE : Doc.SOFT_LINE;
    }

    private Doc joined(final List<Link> links) {
        return new Doc.Concat(links.stream()
                .gather(Pairs.withPrevious(MethodChainRenderer::joinedTo))
                .flatMap(Function.identity())
                .toList());
    }

    private Stream<Doc> joinedTo(final Optional<Link> previous, final Link link) {
        return previous.stream()
                        .anyMatch(Link::endsTheLine)
                ? Stream.of(new Doc.Text(" "), link.doc())
                : Stream.of(link.doc());
    }

    private List<Link> links(final MethodInvocationTree call, final boolean lastCall, final Recursor recursor) {
        if (call.getMethodSelect() instanceof MemberSelectTree ms) {
            final var head = new Doc.Concat(
                    Stream.concat(
                            Stream.of(new Doc.Text(".")),
                            Stream.concat(
                                    TypeRenderers.typeArguments(call.getTypeArguments(), recursor),
                                    Stream.of(new Doc.Text(ms.getIdentifier().toString())))));
            final var arguments = CallArguments.render(call, call.getArguments(), lastCall, recursor);
            final var callSuffix = Doc.concat(head, arguments.doc());
            final var onItsOwnLine = Doc.concat(head, arguments.onItsOwnLine());
            final var endsTheFirstLine = FIRST_LINE_ENDS.contains(
                    ms.getIdentifier().toString());
            if (ms.getExpression() instanceof MethodInvocationTree) {
                return List.of(new Link(callSuffix, onItsOwnLine, false, arguments.hugsLastArgument(), endsTheFirstLine));
            }
            final var receiver = recursor.scan(ms.getExpression());
            if (createsABuilder(ms.getExpression())) {
                final var commented = Doc.endsWithLineComment(receiver);
                return List.of(
                        new Link(receiver, receiver, commented, false, true),
                        new Link(callSuffix, onItsOwnLine, false, arguments.hugsLastArgument(), endsTheFirstLine));
            }
            final var first = Doc.concat(receiver, callSuffix);
            return List.of(new Link(first, first, false, arguments.hugsLastArgument(), endsTheFirstLine));
        }
        final var head = new Doc.Concat(
                Stream.concat(TypeRenderers.typeArguments(call.getTypeArguments(), recursor), Stream.of(recursor.scan(call.getMethodSelect()))));
        final var arguments = CallArguments.render(call, call.getArguments(), lastCall, recursor);
        final var first = Doc.concat(head, arguments.doc());
        return List.of(new Link(first, first, false, arguments.hugsLastArgument(), false));
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
                true,
                false,
                link.endsTheFirstLine());
    }

    private record Link(Doc doc, Doc onItsOwnLine, boolean endsTheLine, boolean hugsLastArgument, boolean endsTheFirstLine) {}

    record ChainDocs(Doc doc, Optional<Doc> brokenAfterHead) {}
}
