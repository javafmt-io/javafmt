package io.javafmt.builder;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
final class MethodChainRenderer {

    private final Set<String> STREAMS = Set.of("stream", "parallelStream");

    Doc render(final MethodInvocationTree node, final Recursor recursor) {
        final var calls = Stream.<@Nullable MethodInvocationTree>iterate(
                node, Objects::nonNull,
                call -> call.getMethodSelect() instanceof MemberSelectTree ms && ms.getExpression() instanceof MethodInvocationTree receiver
                        ? receiver
                        : null)
                .map(Objects::requireNonNull)
                .toList();
        if (calls.size() == 1) {
            return link(node, recursor).doc();
        }
        final var links = calls.stream()
                .map(call -> call == node ? link(call, recursor) : withTrailingComments(link(call, recursor), recursor.trailing(call)))
                .toList()
                .reversed();
        final var headEnd = streamHeadEnd(calls.reversed(), links);
        final var broken = headEnd == 0
                ? chainGroup(Doc.concat(links.getFirst().doc(), Doc.continuationIndent(tail(links, 1, MethodChainRenderer::separatorAfter))))
                : new Doc.Group(
                        new Doc.IfBreak(
                                new Doc.Choice(brokenAfter(links, headEnd), brokenAfter(links, 0), Doc.Choice.Fit.OPENING_LINE), joined(links)),
                        Doc.flatWidthOrBreak(joined(links)) < 0);
        if (!hugsLastLink(links)) {
            return broken;
        }
        return new Doc.Choice(joined(links), broken, Doc.Choice.Fit.OPENING_LINE);
    }

    private int streamHeadEnd(final List<MethodInvocationTree> calls, final List<Link> links) {
        final var stream = IntStream.range(1, calls.size())
                .filter(i -> calls.get(i).getMethodSelect() instanceof MemberSelectTree ms && STREAMS.contains(ms.getIdentifier().toString()))
                .findFirst()
                .orElse(0);
        return links.subList(0, stream).stream().anyMatch(Link::endsTheLine) ? 0 : stream;
    }

    private boolean hugsLastLink(final List<Link> links) {
        return links.getLast().hugsLastArgument()
            && links.subList(0, links.size() - 1).stream().noneMatch(link -> link.endsTheLine() || Doc.flatWidthOrBreak(link.doc()) < 0);
    }

    Doc chainGroup(final Doc contents) {
        return new Doc.Group(contents, Doc.flatWidthOrBreak(contents) < 0);
    }

    private Doc brokenAfter(final List<Link> links, final int headEnd) {
        return Doc.concat(joined(links.subList(0, headEnd + 1)), Doc.continuationIndent(tail(links, headEnd + 1, _ -> Doc.HARD_LINE)));
    }

    private Doc tail(final List<Link> links, final int from, final Function<Link, Doc> separator) {
        return new Doc.Concat(
                IntStream.range(from, links.size()).boxed().flatMap(i -> Stream.of(separator.apply(links.get(i - 1)), links.get(i).doc())).toList());
    }

    private Doc separatorAfter(final Link link) {
        if (link.endsTheLine()) {
            return Doc.HARD_LINE;
        }
        return link.endsWithComment() ? Doc.LINE : Doc.SOFT_LINE;
    }

    private Doc joined(final List<Link> links) {
        return new Doc.Concat(
                IntStream.range(0, links.size())
                        .boxed()
                        .flatMap(
                                i -> i > 0 && links.get(i - 1).endsWithComment()
                                        ? Stream.of(new Doc.Text(" "), links.get(i).doc())
                                        : Stream.of(links.get(i).doc()))
                        .toList());
    }

    private Link link(final MethodInvocationTree call, final Recursor recursor) {
        if (call.getMethodSelect() instanceof MemberSelectTree ms) {
            final var head = new Doc.Concat(
                    Stream.concat(
                            Stream.of(new Doc.Text(".")),
                            Stream.concat(typeArguments(call, recursor), Stream.of(new Doc.Text(ms.getIdentifier().toString())))));
            final var arguments = CallArguments.render(call, call.getArguments(), recursor);
            final var callSuffix = Doc.concat(head, arguments.doc());
            if (ms.getExpression() instanceof MethodInvocationTree) {
                return new Link(callSuffix, false, false, arguments.hugsLastArgument());
            }
            return new Link(Doc.concat(recursor.scan(ms.getExpression()), callSuffix), false, false, arguments.hugsLastArgument());
        }
        final var head = new Doc.Concat(Stream.concat(typeArguments(call, recursor), Stream.of(recursor.scan(call.getMethodSelect()))));
        final var arguments = CallArguments.render(call, call.getArguments(), recursor);
        return new Link(Doc.concat(head, arguments.doc()), false, false, arguments.hugsLastArgument());
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
                Doc.concatOf(
                        Stream.of(Stream.of(link.doc()), comments.stream().flatMap(c -> Stream.of(new Doc.Text(" "), CommentDocs.renderComment(c))))),
                comments.stream().anyMatch(c -> c.text().startsWith("//")), true, false);
    }

    private Stream<Doc> typeArguments(final MethodInvocationTree mit, final Recursor recursor) {
        final var arguments = mit.getTypeArguments();
        if (arguments.isEmpty()) {
            return Stream.empty();
        }
        return Stream.concat(
                Stream.of(new Doc.Text("<")),
                Stream.concat(Doc.intersperse(new Doc.Text(", "), arguments.stream().map(recursor::scan)), Stream.of(new Doc.Text(">"))));
    }

    /**
     * One link of the chain. {@code endsTheLine} marks a link whose own trailing {@code //}
     * comment closes the line, so the link after it has to start a new one however the enclosing
     * group prints.
     */
    private record Link(Doc doc, boolean endsTheLine, boolean endsWithComment, boolean hugsLastArgument) {}
}
