package io.javafmt.builder;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
final class MethodChainRenderer {
    /**
     * In compact context (e.g. an operand of a broken logical chain), the author-broke
     * heuristic below is skipped — only a hard newline inside the chain forces a break.
     */
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
        final var tail = new Doc.Concat(
                IntStream.range(1, links.size())
                        .boxed()
                        .flatMap(i -> Stream.of(links.get(i - 1).endsTheLine() ? Doc.HARD_LINE : Doc.SOFT_LINE, links.get(i).doc())));
        final var contents = Doc.concat(links.getFirst().doc(), Doc.continuationIndent(tail));
        final var authorBroke = !recursor.inCompactContext() && authorBrokeChain(calls, recursor);
        final var broken = chainGroup(contents, authorBroke);
        if (authorBroke || !hugsLastLink(links)) {
            return broken;
        }
        return new Doc.Choice(new Doc.Concat(links.stream().map(Link::doc)), broken, Doc.Choice.Fit.OPENING_LINE);
    }

    private boolean hugsLastLink(final List<Link> links) {
        return links.getLast().hugsLastArgument()
            && links.subList(0, links.size() - 1).stream().noneMatch(link -> link.endsTheLine() || Doc.flatWidthOrBreak(link.doc()) < 0);
    }

    Doc chainGroup(final Doc contents, final boolean authorBroke) {
        return new Doc.Group(contents, authorBroke || Doc.flatWidthOrBreak(contents) < 0);
    }

    /**
     * Returns true if the author placed any {@code .} token of the chain on a new line. Checks
     * {@link Recursor#isFollowedByNewline} on each receiver expression — if the source has a
     * newline between a receiver and its trailing {@code .}, that's an explicit multi-line layout
     * the formatter should preserve.
     */
    private boolean authorBrokeChain(final List<MethodInvocationTree> calls, final Recursor recursor) {
        return calls.stream()
                .anyMatch(call -> call.getMethodSelect() instanceof MemberSelectTree ms && recursor.isFollowedByNewline(ms.getExpression()));
    }

    private Link link(final MethodInvocationTree call, final Recursor recursor) {
        if (call.getMethodSelect() instanceof MemberSelectTree ms) {
            final var head = new Doc.Concat(
                    Stream.concat(
                            Stream.of(new Doc.Text(".")),
                            Stream.concat(typeArguments(call, recursor), Stream.of(new Doc.Text(ms.getIdentifier().toString())))));
            final var arguments = CallArguments.render(call.getArguments(), recursor);
            final var callSuffix = Doc.concat(head, arguments.doc());
            if (ms.getExpression() instanceof MethodInvocationTree) {
                return new Link(callSuffix, false, arguments.hugsLastArgument());
            }
            return new Link(Doc.concat(recursor.scan(ms.getExpression()), callSuffix), false, arguments.hugsLastArgument());
        }
        final var head = new Doc.Concat(Stream.concat(typeArguments(call, recursor), Stream.of(recursor.scan(call.getMethodSelect()))));
        final var arguments = CallArguments.render(call.getArguments(), recursor);
        return new Link(Doc.concat(head, arguments.doc()), false, arguments.hugsLastArgument());
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
                comments.stream().anyMatch(c -> c.text().startsWith("//")), false);
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
    private record Link(Doc doc, boolean endsTheLine, boolean hugsLastArgument) {}
}
