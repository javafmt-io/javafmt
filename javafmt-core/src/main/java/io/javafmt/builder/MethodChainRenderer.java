package io.javafmt.builder;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import io.javafmt.parser.CommentToken;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class MethodChainRenderer {

    /**
     * Palantir Java Format breaks chained method calls when they'd exceed this many chars on
     * one line, even when the surrounding line width is much wider. Matches their reasoning:
     * chains tend to compose, and breaking them aggressively yields better reviewability than
     * packing them to the line width. This threshold combines with
     * {@link #MIN_LINKS_FOR_THRESHOLD_BREAK}: a 2-link chain ({@code receiver.method(args)})
     * gains nothing from breaking — the long arg list ends up on a continuation line either
     * way, and the only payoff would be moving the trailing {@code .method} down on its own.
     * Skipping the break for 2-link chains keeps short chains compact and restricts the
     * break-cascade to genuine builder/stream pipelines.
     */
    private final int CHAIN_BREAK_THRESHOLD = 80;

    private final int MIN_LINKS_FOR_THRESHOLD_BREAK = 3;

    /**
     * In compact context (e.g. an operand of a broken logical chain), the threshold and
     * author-broke heuristics below are skipped — only a hard newline inside the chain forces
     * a break.
     */
    Doc render(final MethodInvocationTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var links = collectLinks(node, recursor, attacher);
        if (links.size() == 1) {
            return links.getFirst().doc();
        }
        final var tail = new Doc.Concat(
                IntStream.range(1, links.size())
                        .boxed()
                        .flatMap(i -> Stream.of(links.get(i - 1).endsTheLine() ? Doc.HARD_LINE : Doc.SOFT_LINE, links.get(i).doc())));
        final var contents = new Doc.Concat(List.of(links.getFirst().doc(), Doc.continuationIndent(tail)));
        final var flatWidth = Doc.flatWidthOrBreak(contents);
        final var hitsThreshold = !recursor.inCompactContext()
            && links.size() >= MIN_LINKS_FOR_THRESHOLD_BREAK
            && flatWidth > CHAIN_BREAK_THRESHOLD;
        final var preferAuthor = !recursor.inCompactContext() && authorBrokeChain(node, recursor);
        final var preferBreak = preferAuthor || flatWidth < 0 || hitsThreshold;
        return new Doc.Group(contents, preferBreak);
    }

    /**
     * Walks the chain receiver-by-receiver and returns true if the author placed any {@code .}
     * token on a new line. Checks {@link Recursor#isFollowedByNewline} on each receiver
     * expression — if the source has a newline between a receiver and its trailing {@code .},
     * that's an explicit multi-line layout the formatter should preserve.
     */
    private boolean authorBrokeChain(final Tree expr, final Recursor recursor) {
        if (expr instanceof MethodInvocationTree mit && mit.getMethodSelect() instanceof MemberSelectTree ms) {
            if (recursor.isFollowedByNewline(ms.getExpression())) {
                return true;
            }
            return authorBrokeChain(ms.getExpression(), recursor);
        }
        return false;
    }

    private List<Link> collectLinks(final Tree expr, final Recursor recursor, final LeadingCommentAttacher attacher) {
        if (expr instanceof MethodInvocationTree mit && mit.getMethodSelect() instanceof MemberSelectTree ms) {
            final var head = new Doc.Concat(
                    Stream.concat(
                            Stream.of(new Doc.Text(".")),
                            Stream.concat(typeArgs(mit, recursor), Stream.of(new Doc.Text(ms.getIdentifier().toString())))));
            final var callSuffix = buildCallSuffix(head, mit.getArguments(), recursor, attacher);
            if (ms.getExpression() instanceof MethodInvocationTree receiver) {
                final var received = collectLinks(receiver, recursor, attacher);
                return Stream.concat(withTrailingComments(received, attacher.trailing(receiver)).stream(), Stream.of(new Link(callSuffix))).toList();
            }
            return List.of(new Link(new Doc.Concat(List.of(recursor.scan(ms.getExpression()), callSuffix))));
        }
        if (expr instanceof MethodInvocationTree mit) {
            final var head = new Doc.Concat(Stream.concat(typeArgs(mit, recursor), Stream.of(recursor.scan(mit.getMethodSelect()))));
            return List.of(new Link(buildCallSuffix(head, mit.getArguments(), recursor, attacher)));
        }
        return List.of(new Link(recursor.scan(expr)));
    }

    /**
     * The receiver's links with {@code comments} hung off the last of them. The comments belong to
     * the link the author wrote them beside; attaching them to the chain as a whole moved them
     * above the statement, where they read as annotating all of it.
     */
    private List<Link> withTrailingComments(final List<Link> links, final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return links;
        }
        final var last = links.getLast();
        final var decorated = new Link(
                Doc.concatOf(Stream.of(last.doc()), comments.stream().flatMap(c -> Stream.of(new Doc.Text(" "), CommentDocs.renderComment(c)))),
                comments.stream().anyMatch(c -> c.text().startsWith("//")));
        return Stream.concat(links.stream().limit(links.size() - 1), Stream.of(decorated)).toList();
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

    private Doc buildCallSuffix(
            final Doc head,
            final List<? extends ExpressionTree> args,
            final Recursor recursor,
            final LeadingCommentAttacher attacher) {
        if (args.isEmpty()) {
            return new Doc.Concat(List.of(head, new Doc.Text("()")));
        }
        if (args.size() == 1
            && args.getFirst() instanceof LambdaExpressionTree lam
            && lam.getBodyKind() == LambdaExpressionTree.BodyKind.STATEMENT) {
            return new Doc.Concat(List.of(head, new Doc.Text("("), recursor.scan(lam), new Doc.Text(")")));
        }
        final var interior = new Doc.Concat(
                Stream.concat(
                        Stream.of(Doc.SOFT_LINE),
                        IntStream.range(0, args.size())
                                .boxed()
                                .flatMap(
                                        i -> Stream.concat(
                                                i == 0
                                                        ? Stream.<Doc>empty()
                                                        : CommentDocs.listSeparator(",", attacher.interior(args.get(i))).stream(),
                                                Stream.of(recursor.scan(args.get(i)))))));
        return new Doc.Group(new Doc.Concat(List.of(head, new Doc.Text("("), Doc.continuationIndent(interior), new Doc.Text(")"))));
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
