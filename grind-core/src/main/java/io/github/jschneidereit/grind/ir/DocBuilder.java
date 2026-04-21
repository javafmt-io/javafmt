package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
        final var parts = new ArrayList<Doc>();

        if (node.getPackageName() != null) {
            parts.add(new Doc.Text("package " + node.getPackageName() + ";"));
            parts.add(new Doc.HardLine());
        }

        for (final var decl : node.getTypeDecls()) {
            final var declDoc = scan(decl, null);
            if (declDoc != null) {
                parts.add(declDoc);
            }
        }

        return new Doc.Concat(parts);
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
            return new Doc.Text(header.toString());
        }

        header.append(" {");
        final var parts = new ArrayList<Doc>();
        parts.add(new Doc.Text(header.toString()));

        for (var i = 0; i < members.size(); i++) {
            if (i > 0) {
                parts.add(new Doc.HardLine());
            }
            parts.add(new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), members.get(i)))));
        }

        parts.add(new Doc.HardLine());
        parts.add(new Doc.Text("}"));
        return new Doc.Concat(parts);
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
            final var compParts = new ArrayList<Doc>();
            compParts.add(new Doc.SoftLine());
            for (var i = 0; i < components.size(); i++) {
                if (i > 0) {
                    compParts.add(new Doc.Text(","));
                    compParts.add(new Doc.Line());
                }
                final var comp = components.get(i);
                compParts.add(new Doc.Text(comp.getType() + " " + comp.getName()));
            }
            componentListDoc = new Doc.Concat(List.of(
                new Doc.Text("("),
                new Doc.Indent(new Doc.Concat(compParts)),
                new Doc.SoftLine(),
                new Doc.Text(")")
            ));
        }

        if (bodyMembers.isEmpty()) {
            return new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text(prefix.toString()),
                componentListDoc,
                new Doc.Text(" {}")
            )));
        }

        final var parts = new ArrayList<Doc>();
        parts.add(new Doc.Text(prefix.toString()));
        parts.add(componentListDoc);
        parts.add(new Doc.Text(" {"));
        for (var i = 0; i < bodyMembers.size(); i++) {
            if (i > 0) {
                parts.add(new Doc.HardLine());
            }
            parts.add(new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), bodyMembers.get(i)))));
        }
        parts.add(new Doc.HardLine());
        parts.add(new Doc.Text("}"));
        return new Doc.Concat(parts);
    }

    @Override
    public @Nullable Doc visitVariable(final VariableTree node, final Void p) {
        final var sb = new StringBuilder();
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
        final var sb = new StringBuilder();
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
        if (node.getBody() != null) {
            sb.append(" {}");
        }
        return new Doc.Text(sb.toString());
    }

    private static void renderModifiers(final ModifiersTree mods, final StringBuilder sb) {
        final var flags = mods.getFlags();
        if (!flags.isEmpty()) {
            sb.append(flags.stream().map(Modifier::toString).collect(Collectors.joining(" ")));
            sb.append(" ");
        }
    }
}
