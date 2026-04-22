package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;

import java.util.List;
import java.util.stream.Stream;

final class MethodChainRenderer {

    static Doc render(final MethodInvocationTree node, final Recursor recursor) {
        final var links = collectLinks(node, recursor);
        if (links.size() == 1) {
            return links.get(0);
        }
        final var tail = new Doc.Concat(links.stream()
            .skip(1)
            .flatMap(link -> Stream.<Doc>of(new Doc.SoftLine(), link)));
        return new Doc.Group(new Doc.Concat(List.of(
            links.get(0),
            new Doc.Indent(new Doc.Indent(tail))
        )));
    }

    private static List<Doc> collectLinks(final Tree expr, final Recursor recursor) {
        if (expr instanceof MethodInvocationTree mit
            && mit.getMethodSelect() instanceof MemberSelectTree ms) {
            final var callSuffix = buildCallSuffix("." + ms.getIdentifier(), mit.getArguments(), recursor);
            if (ms.getExpression() instanceof MethodInvocationTree) {
                return Stream.concat(
                    collectLinks(ms.getExpression(), recursor).stream(),
                    Stream.<Doc>of(callSuffix)
                ).toList();
            }
            return List.of(new Doc.Concat(List.of(renderNonChain(ms.getExpression(), recursor), callSuffix)));
        }
        if (expr instanceof MethodInvocationTree mit) {
            return List.of(buildCallSuffix(mit.getMethodSelect().toString(), mit.getArguments(), recursor));
        }
        return List.of(renderNonChain(expr, recursor));
    }

    private static Doc buildCallSuffix(final String head, final List<? extends ExpressionTree> args, final Recursor recursor) {
        final var parts = Stream.<Doc>concat(
            Stream.of(new Doc.Text(head + "(")),
            Stream.concat(renderArgs(args, recursor), Stream.of(new Doc.Text(")")))
        );
        return new Doc.Concat(parts);
    }

    private static Stream<Doc> renderArgs(final List<? extends ExpressionTree> args, final Recursor recursor) {
        return args.stream()
            .<Doc>map(arg -> renderArg(arg, recursor))
            .flatMap(d -> Stream.<Doc>of(new Doc.Text(", "), d))
            .skip(1);
    }

    private static Doc renderArg(final ExpressionTree arg, final Recursor recursor) {
        final var scanned = recursor.scan(arg);
        return scanned != null ? scanned : new Doc.Text(arg.toString());
    }

    private static Doc renderNonChain(final Tree tree, final Recursor recursor) {
        final var scanned = recursor.scan(tree);
        return scanned != null ? scanned : new Doc.Text(tree.toString());
    }

    private MethodChainRenderer() {}
}
