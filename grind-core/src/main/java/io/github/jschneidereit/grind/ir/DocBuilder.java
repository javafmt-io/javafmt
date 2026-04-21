package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

import org.jspecify.annotations.Nullable;

public final class DocBuilder extends TreeScanner<@Nullable Doc, Void> {

    private DocBuilder() {}

    public static Doc build(final CompilationUnitTree tree) {
        Objects.requireNonNull(tree, "tree");
        final var doc = new DocBuilder().visitCompilationUnit(tree, null);
        return Objects.requireNonNull(doc, "visitCompilationUnit returned null");
    }

    @Override
    public @Nullable Doc visitCompilationUnit(final CompilationUnitTree node, final Void p) {
        final var pkgStream = node.getPackageName() != null
            ? Stream.<Doc>of(new Doc.Text("package " + node.getPackageName() + ";"), new Doc.HardLine())
            : Stream.<Doc>empty();
        return new Doc.Concat(Stream.concat(
            pkgStream,
            node.getTypeDecls().stream()
                .flatMap(decl -> Optional.ofNullable(scan(decl, null)).stream())
        ));
    }

    @Override
    public @Nullable Doc visitClass(final ClassTree node, final Void p) {
        if (node.getKind() == Tree.Kind.RECORD) {
            return buildRecord(node);
        }

        final var header = new StringBuilder();
        renderModifiers(node.getModifiers(), header);
        header.append("class ").append(node.getSimpleName());

        final var members = node.getMembers().stream()
            .filter(m -> m instanceof VariableTree
                || (m instanceof MethodTree mt && !mt.getName().contentEquals("<init>")))
            .map(m -> scan(m, null))
            .filter(Objects::nonNull)
            .toList();

        if (members.isEmpty()) {
            header.append(" {}");
            return prependOwnLineAnnotations(node.getModifiers(), new Doc.Text(header.toString()));
        }

        header.append(" {");
        return prependOwnLineAnnotations(node.getModifiers(), new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>concat(
                Stream.of(new Doc.Text(header.toString())),
                members.stream()
                    .flatMap(m -> Stream.<Doc>of(
                        new Doc.HardLine(),
                        new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), m)))
                    ))
                    .skip(1)
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        )));
    }

    private Doc buildRecord(final ClassTree node) {
        final var prefix = new StringBuilder();
        renderModifiers(node.getModifiers(), prefix);
        prefix.append("record ").append(node.getSimpleName());

        // Java forbids non-static instance fields in a record body, so non-static
        // VariableTrees are always record components (from the header's `()` list).
        final var components = node.getMembers().stream()
            .filter(m -> m instanceof VariableTree v
                && !v.getModifiers().getFlags().contains(Modifier.STATIC))
            .map(m -> (VariableTree) m)
            .toList();

        final var bodyMembers = node.getMembers().stream()
            .filter(m -> !(m instanceof VariableTree v
                && !v.getModifiers().getFlags().contains(Modifier.STATIC))
                && !(m instanceof MethodTree mt && mt.getName().contentEquals("<init>")))
            .map(m -> scan(m, null))
            .filter(Objects::nonNull)
            .toList();

        final Doc componentListDoc;
        if (components.isEmpty()) {
            componentListDoc = new Doc.Text("()");
        } else {
            componentListDoc = new Doc.Concat(List.of(
                new Doc.Text("("),
                new Doc.Indent(new Doc.Concat(Stream.concat(
                    Stream.<Doc>of(new Doc.SoftLine()),
                    components.stream()
                        .flatMap(comp -> Stream.<Doc>of(
                            new Doc.Text(","),
                            new Doc.Line(),
                            new Doc.Text(comp.getType() + " " + comp.getName())
                        ))
                        .skip(2)
                ))),
                new Doc.SoftLine(),
                new Doc.Text(")")
            ));
        }

        if (bodyMembers.isEmpty()) {
            return prependOwnLineAnnotations(node.getModifiers(), new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text(prefix.toString()),
                componentListDoc,
                new Doc.Text(" {}")
            ))));
        }

        return prependOwnLineAnnotations(node.getModifiers(), new Doc.Concat(Stream.<Doc>concat(
            Stream.<Doc>concat(
                Stream.of(new Doc.Text(prefix.toString()), componentListDoc, new Doc.Text(" {")),
                bodyMembers.stream()
                    .flatMap(m -> Stream.<Doc>of(
                        new Doc.HardLine(),
                        new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), m)))
                    ))
                    .skip(1)
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        )));
    }

    @Override
    public @Nullable Doc visitVariable(final VariableTree node, final Void p) {
        final var sb = new StringBuilder();
        renderAnnotations(node.getModifiers(), sb);
        renderModifiers(node.getModifiers(), sb);
        sb.append(node.getType());
        sb.append(" ");
        sb.append(node.getName());
        if (node.getInitializer() != null) {
            sb.append(" = ").append(node.getInitializer());
        }
        sb.append(";");
        return new Doc.Text(sb.toString());
    }

    @Override
    public @Nullable Doc visitMethod(final MethodTree node, final Void p) {
        if (node.getName().contentEquals("<init>")) {
            return null;
        }
        final var annotations = node.getModifiers().getAnnotations();
        final var inlineAnnotation = annotations.size() == 1 && annotations.get(0).getArguments().isEmpty();

        final var sb = new StringBuilder();
        if (inlineAnnotation) {
            renderAnnotations(node.getModifiers(), sb);
        }
        renderModifiers(node.getModifiers(), sb);
        if (node.getReturnType() != null) {
            sb.append(node.getReturnType());
            sb.append(" ");
        }
        sb.append(node.getName());
        sb.append("(");
        final var params = node.getParameters().stream()
            .map(param -> param.getType() + " " + param.getName())
            .collect(Collectors.joining(", "));
        sb.append(params);
        sb.append(")");

        final Doc signatureDoc;
        if (node.getBody() != null) {
            final var stmts = node.getBody().getStatements();
            if (stmts.isEmpty()) {
                sb.append(" {}");
                signatureDoc = new Doc.Text(sb.toString());
            } else {
                signatureDoc = new Doc.Concat(Stream.<Doc>concat(
                    Stream.<Doc>concat(
                        Stream.of(new Doc.Text(sb + " {")),
                        stmts.stream()
                            .flatMap(stmt -> Optional.ofNullable(scan(stmt, null)).stream())
                            .map(stmtDoc -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), stmtDoc))))
                    ),
                    Stream.of(new Doc.HardLine(), new Doc.Text("}"))
                ));
            }
        } else {
            signatureDoc = new Doc.Text(sb.toString());
        }

        return inlineAnnotation ? signatureDoc : prependOwnLineAnnotations(node.getModifiers(), signatureDoc);
    }

    @Override
    public @Nullable Doc visitReturn(final ReturnTree node, final Void p) {
        if (node.getExpression() == null) {
            return new Doc.Text("return;");
        }
        return new Doc.Text("return " + node.getExpression() + ";");
    }

    @Override
    public @Nullable Doc visitExpressionStatement(final ExpressionStatementTree node, final Void p) {
        return new Doc.Text(node.getExpression() + ";");
    }

    private static Doc prependOwnLineAnnotations(final ModifiersTree mods, final Doc doc) {
        final var annotations = mods.getAnnotations();
        if (annotations.isEmpty()) {
            return doc;
        }
        return new Doc.Concat(Stream.concat(
            annotations.stream()
                .flatMap(a -> Stream.<Doc>of(new Doc.Text(a.toString()), new Doc.HardLine())),
            Stream.of(doc)
        ));
    }

    private static void renderAnnotations(final ModifiersTree mods, final StringBuilder sb) {
        for (final var annotation : mods.getAnnotations()) {
            sb.append(annotation).append(" ");
        }
    }

    private static void renderModifiers(final ModifiersTree mods, final StringBuilder sb) {
        final var flags = mods.getFlags();
        if (!flags.isEmpty()) {
            sb.append(flags.stream().map(Modifier::toString).collect(Collectors.joining(" ")));
            sb.append(" ");
        }
    }
}
