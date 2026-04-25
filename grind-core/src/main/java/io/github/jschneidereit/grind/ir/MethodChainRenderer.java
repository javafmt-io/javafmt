package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;

import java.util.List;
import java.util.stream.Collectors;
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
            final var callSuffix = buildCallSuffix("." + typeArgs(mit) + ms.getIdentifier(), mit.getArguments(), recursor);
            if (ms.getExpression() instanceof MethodInvocationTree) {
                return Stream.concat(
                    collectLinks(ms.getExpression(), recursor).stream(),
                    Stream.<Doc>of(callSuffix)
                ).toList();
            }
            return List.of(new Doc.Concat(List.of(renderNonChain(ms.getExpression(), recursor), callSuffix)));
        }
        if (expr instanceof MethodInvocationTree mit) {
            return List.of(buildCallSuffix(typeArgs(mit) + mit.getMethodSelect(), mit.getArguments(), recursor));
        }
        return List.of(renderNonChain(expr, recursor));
    }

    private static String typeArgs(final MethodInvocationTree mit) {
        final var args = mit.getTypeArguments();
        if (args.isEmpty()) {
            return "";
        }
        return args.stream()
            .map(Object::toString)
            .collect(Collectors.joining(", ", "<", ">"));
    }

    private static Doc buildCallSuffix(final String head, final List<? extends ExpressionTree> args, final Recursor recursor) {
        if (args.isEmpty()) {
            return new Doc.Text(head + "()");
        }
        final var interior = new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>of(new Doc.SoftLine()),
            Doc.intersperse(List.of(new Doc.Text(","), new Doc.Line()), args.stream()
                .<Doc>map(arg -> renderArg(arg, recursor)))
        ));
        return new Doc.Group(new Doc.Concat(List.of(
            new Doc.Text(head + "("),
            new Doc.Indent(interior),
            new Doc.SoftLine(),
            new Doc.Text(")")
        )));
    }

    private static Doc renderArg(final ExpressionTree arg, final Recursor recursor) {
        return recursor.scan(arg);
    }

    private static Doc renderNonChain(final Tree tree, final Recursor recursor) {
        return recursor.scan(tree);
    }

    private MethodChainRenderer() {}
}
