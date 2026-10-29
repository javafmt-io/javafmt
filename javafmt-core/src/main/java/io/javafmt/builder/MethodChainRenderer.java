package io.javafmt.builder;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentToken;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

@UtilityClass
final class MethodChainRenderer {

    /**
     * In compact context (e.g. an operand of a broken logical chain), the author-broke
     * heuristic below is skipped — only a hard newline inside the chain forces a break.
     */
    Doc render(final MethodInvocationTree node, final Recursor recursor) {
        final var calls = Stream.<@Nullable MethodInvocationTree>iterate(
                node,
                Objects::nonNull,
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
        return chainGroup(contents, !recursor.inCompactContext() && authorBrokeChain(calls, recursor));
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
                            Stream.concat(typeArgs(call, recursor), Stream.of(new Doc.Text(ms.getIdentifier().toString())))));
            final var callSuffix = buildCallSuffix(head, call.getArguments(), recursor);
            if (ms.getExpression() instanceof MethodInvocationTree) {
                return new Link(callSuffix);
            }
            return new Link(Doc.concat(recursor.scan(ms.getExpression()), callSuffix));
        }
        final var head = new Doc.Concat(Stream.concat(typeArgs(call, recursor), Stream.of(recursor.scan(call.getMethodSelect()))));
        return new Link(buildCallSuffix(head, call.getArguments(), recursor));
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
                comments.stream().anyMatch(c -> c.text().startsWith("//")));
    }

    private Stream<Doc> typeArgs(final MethodInvocationTree mit, final Recursor recursor) {
        final var args = mit.getTypeArguments();
        if (args.isEmpty()) {
            return Stream.empty();
        }
        return Stream.concat(
                Stream.of(new Doc.Text("<")),
                Stream.concat(Doc.intersperse(new Doc.Text(", "), args.stream().map(recursor::scan)), Stream.of(new Doc.Text(">"))));
    }

    private Doc buildCallSuffix(final Doc head, final List<? extends ExpressionTree> args, final Recursor recursor) {
        if (args.isEmpty()) {
            return Doc.concat(head, new Doc.Text("()"));
        }
        if (args.size() == 1
            && args.getFirst() instanceof LambdaExpressionTree lam
            && lam.getBodyKind() == LambdaExpressionTree.BodyKind.STATEMENT) {
            return Doc.concat(head, new Doc.Text("("), recursor.scan(lam), new Doc.Text(")"));
        }
        final var interior = new Doc.Concat(Stream.concat(Stream.of(Doc.SOFT_LINE), CommentDocs.commaList(args, recursor, recursor::scan)));
        return new Doc.Group(Doc.concat(head, new Doc.Text("("), Doc.continuationIndent(interior), new Doc.Text(")")));
    }

    /**
     * One link of the chain. {@code endsTheLine} marks a link whose own trailing {@code //}
     * comment closes the line, so the link after it has to start a new one however the enclosing
     * group prints.
     */
    private record Link(Doc doc, boolean endsTheLine) {

        Link(final Doc doc) {
            this(doc, false);
        }
    }
}
