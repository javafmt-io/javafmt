package io.javafmt.builder;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;

import java.util.List;
import java.util.stream.Stream;
import io.javafmt.doc.Doc;

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
            final var head = new Doc.Concat(Stream.concat(
                Stream.<Doc>of(new Doc.Text(".")),
                Stream.concat(typeArgs(mit, recursor), Stream.<Doc>of(new Doc.Text(ms.getIdentifier().toString())))));
            final var callSuffix = buildCallSuffix(head, mit.getArguments(), recursor);
            if (ms.getExpression() instanceof MethodInvocationTree) {
                return Stream.concat(
                    collectLinks(ms.getExpression(), recursor).stream(),
                    Stream.<Doc>of(callSuffix)
                ).toList();
            }
            return List.of(new Doc.Concat(List.of(recursor.scan(ms.getExpression()), callSuffix)));
        }
        if (expr instanceof MethodInvocationTree mit) {
            final var head = new Doc.Concat(Stream.concat(
                typeArgs(mit, recursor),
                Stream.<Doc>of(recursor.scan(mit.getMethodSelect()))));
            return List.of(buildCallSuffix(head, mit.getArguments(), recursor));
        }
        return List.of(recursor.scan(expr));
    }

    private static Stream<Doc> typeArgs(final MethodInvocationTree mit, final Recursor recursor) {
        final var args = mit.getTypeArguments();
        if (args.isEmpty()) {
            return Stream.empty();
        }
        return Stream.concat(
            Stream.<Doc>of(new Doc.Text("<")),
            Stream.concat(
                Doc.intersperse(new Doc.Text(", "), args.stream().<Doc>map(recursor::scan)),
                Stream.<Doc>of(new Doc.Text(">"))));
    }

    private static Doc buildCallSuffix(final Doc head, final List<? extends ExpressionTree> args, final Recursor recursor) {
        if (args.isEmpty()) {
            return new Doc.Concat(List.of(head, new Doc.Text("()")));
        }
        final var interior = new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>of(new Doc.SoftLine()),
            Doc.intersperse(List.of(new Doc.Text(","), new Doc.Line()), args.stream()
                .<Doc>map(recursor::scan))
        ));
        return new Doc.Group(new Doc.Concat(List.of(
            head,
            new Doc.Text("("),
            new Doc.Indent(interior),
            new Doc.SoftLine(),
            new Doc.Text(")")
        )));
    }

    private MethodChainRenderer() {}
}
