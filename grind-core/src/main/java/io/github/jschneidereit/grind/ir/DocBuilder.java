package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreeScanner;

import java.util.Comparator;
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
        final var hasPackage = node.getPackageName() != null;
        final var pkgStream = hasPackage
            ? Stream.<Doc>of(new Doc.Text("package " + node.getPackageName() + ";"), new Doc.HardLine())
            : Stream.<Doc>empty();
        return new Doc.Concat(Stream.concat(
            Stream.concat(pkgStream, buildImportSection(hasPackage, node.getImports())),
            node.getTypeDecls().stream()
                .flatMap(decl -> Optional.ofNullable(scan(decl, null)).stream())
        ));
    }

    private static Stream<Doc> buildImportSection(
            final boolean hasPackage,
            final List<? extends ImportTree> imports) {
        if (imports.isEmpty()) {
            return Stream.empty();
        }
        final var statics = imports.stream()
            .filter(ImportTree::isStatic)
            .map(i -> i.getQualifiedIdentifier().toString())
            .sorted()
            .toList();
        final var nonStatics = imports.stream()
            .filter(i -> !i.isStatic())
            .map(i -> i.getQualifiedIdentifier().toString())
            .sorted()
            .toList();
        return Stream.of(
            hasPackage ? Stream.<Doc>of(new Doc.HardLine()) : Stream.<Doc>empty(),
            statics.stream().flatMap(n -> Stream.<Doc>of(new Doc.Text("import static " + n + ";"), new Doc.HardLine())),
            !statics.isEmpty() && !nonStatics.isEmpty() ? Stream.<Doc>of(new Doc.HardLine()) : Stream.<Doc>empty(),
            nonStatics.stream().flatMap(n -> Stream.<Doc>of(new Doc.Text("import " + n + ";"), new Doc.HardLine())),
            Stream.<Doc>of(new Doc.HardLine())
        ).flatMap(s -> s);
    }

    @Override
    public @Nullable Doc visitClass(final ClassTree node, final Void p) {
        return switch (node.getKind()) {
            case RECORD -> buildRecord(node);
            case ENUM -> buildEnum(node);
            case INTERFACE -> buildClassLike(node, "interface");
            default -> buildClassLike(node, "class");
        };
    }

    private Doc buildClassLike(final ClassTree node, final String keyword) {
        final var header = new StringBuilder();
        renderModifiers(node.getModifiers(), header);
        header.append(keyword).append(" ").append(node.getSimpleName());

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

    private Doc buildEnum(final ClassTree node) {
        final var header = new StringBuilder();
        renderModifiers(node.getModifiers(), header);
        header.append("enum ").append(node.getSimpleName());

        final var constants = node.getMembers().stream()
            .filter(m -> m instanceof VariableTree v && v.getInitializer() instanceof NewClassTree)
            .map(m -> (VariableTree) m)
            .sorted(Comparator.comparing(v -> v.getName().toString()))
            .toList();

        final var bodyMembers = node.getMembers().stream()
            .filter(m -> !(m instanceof VariableTree v && v.getInitializer() instanceof NewClassTree)
                && !(m instanceof MethodTree mt && mt.getName().contentEquals("<init>")))
            .map(m -> scan(m, null))
            .filter(Objects::nonNull)
            .toList();

        if (constants.isEmpty() && bodyMembers.isEmpty()) {
            header.append(" {}");
            return prependOwnLineAnnotations(node.getModifiers(), new Doc.Text(header.toString()));
        }

        header.append(" {");

        if (bodyMembers.isEmpty()) {
            // Group: single-line if fits within 150, multi-line with trailing comma if not.
            // Intersperse constants with ", " (flat) or ",\n    " (break), trailing comma only on break.
            final var constantsInner = new Doc.Indent(new Doc.Concat(Stream.concat(
                Stream.<Doc>of(new Doc.Line()),
                Stream.concat(
                    constants.stream()
                        .<Doc>map(v -> new Doc.Text(v.getName().toString()))
                        .flatMap(d -> Stream.<Doc>of(new Doc.Text(","), new Doc.Line(), d))
                        .skip(2),
                    Stream.<Doc>of(new Doc.IfBreak(new Doc.Text(","), new Doc.Text("")))
                )
            )));
            return prependOwnLineAnnotations(node.getModifiers(), new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text(header.toString()),
                constantsInner,
                new Doc.Line(),
                new Doc.Text("}")
            ))));
        }

        // Has body members: always multi-line, constants each on own line with trailing comma.
        final var constantsDocs = constants.stream()
            .<Doc>map(v -> new Doc.Indent(new Doc.Concat(List.of(
                new Doc.HardLine(),
                new Doc.Text(v.getName().toString() + ",")
            ))));

        final Stream<Doc> bodyMembersDocs = Stream.concat(
            Stream.<Doc>of(new Doc.HardLine()),
            bodyMembers.stream()
                .flatMap(m -> Stream.<Doc>of(
                    new Doc.HardLine(),
                    new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), m)))
                ))
                .skip(1)
        );

        return prependOwnLineAnnotations(node.getModifiers(), new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.concat(
                    Stream.of(new Doc.Text(header.toString())),
                    constantsDocs
                ),
                bodyMembersDocs
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
            sb.append(";");
            signatureDoc = new Doc.Text(sb.toString());
        }

        return inlineAnnotation ? signatureDoc : prependOwnLineAnnotations(node.getModifiers(), signatureDoc);
    }

    @Override
    public @Nullable Doc visitReturn(final ReturnTree node, final Void p) {
        if (node.getExpression() == null) {
            return new Doc.Text("return;");
        }
        final var exprDoc = scan(node.getExpression(), null);
        if (exprDoc != null) {
            return new Doc.Concat(List.of(
                new Doc.Text("return "),
                exprDoc,
                new Doc.Text(";")
            ));
        }
        return new Doc.Text("return " + node.getExpression() + ";");
    }

    @Override
    public @Nullable Doc visitExpressionStatement(final ExpressionStatementTree node, final Void p) {
        return new Doc.Text(node.getExpression() + ";");
    }

    @Override
    public @Nullable Doc visitSwitchExpression(final SwitchExpressionTree node, final Void p) {
        // getExpression().toString() already includes surrounding parens, e.g. "(x)"
        final var selectorWithParens = node.getExpression().toString();
        final var caseDocs = node.getCases().stream()
            .flatMap(c -> Optional.ofNullable(scan(c, null)).stream())
            .toList();
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.<Doc>of(new Doc.Text("switch " + selectorWithParens + " {")),
                caseDocs.stream()
                    .map(d -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), d))))
            ),
            Stream.of(new Doc.HardLine(), new Doc.Text("}"))
        ));
    }

    @Override
    public @Nullable Doc visitCase(final CaseTree node, final Void p) {
        if (node.getCaseKind() != CaseTree.CaseKind.RULE) {
            return null;
        }
        final var isDefault = node.getLabels().stream().anyMatch(l -> l instanceof DefaultCaseLabelTree);
        final var labelStr = node.getLabels().stream()
            .map(Object::toString)
            .collect(Collectors.joining(", "));
        final var prefix = isDefault ? "default" : "case " + labelStr;

        final var body = node.getBody();
        if (body == null) {
            return null;
        }
        if (body instanceof BlockTree blockBody) {
            final var stmts = blockBody.getStatements().stream()
                .flatMap(s -> Optional.ofNullable(scan(s, null)).stream())
                .toList();
            return buildBlock(prefix + " ->", stmts);
        }
        return new Doc.Text(prefix + " -> " + body + ";");
    }

    @Override
    public @Nullable Doc visitIf(final IfTree node, final Void p) {
        // javac wraps the condition in JCParens, so toString() already includes the outer ()
        final var cond = node.getCondition().toString();
        final var thenStmts = blockStmts(node.getThenStatement());
        if (node.getElseStatement() == null) {
            return buildBlock("if " + cond, thenStmts);
        }
        if (node.getElseStatement() instanceof IfTree elseIf) {
            final var elseIfDoc = Objects.requireNonNull(scan(elseIf, null));
            return new Doc.Concat(Stream.concat(
                Stream.concat(
                    blockParts("if " + cond, thenStmts),
                    Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("} else "))
                ),
                Stream.<Doc>of(elseIfDoc)
            ));
        }
        final var elseStmts = blockStmts(node.getElseStatement());
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                blockParts("if " + cond, thenStmts),
                Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("} else {"))
            ),
            Stream.concat(
                elseStmts.stream()
                    .<Doc>map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s)))),
                Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("}"))
            )
        ));
    }

    @Override
    public @Nullable Doc visitForLoop(final ForLoopTree node, final Void p) {
        final var init = node.getInitializer().stream()
            .map(s -> stripTrailingSemicolon(s.toString()))
            .collect(Collectors.joining(", "));
        final var cond = node.getCondition() == null ? "" : node.getCondition().toString();
        final var update = node.getUpdate().stream()
            .map(s -> stripTrailingSemicolon(s.toString()))
            .collect(Collectors.joining(", "));
        return buildBlock("for (" + init + "; " + cond + "; " + update + ")", blockStmts(node.getStatement()));
    }

    @Override
    public @Nullable Doc visitEnhancedForLoop(final EnhancedForLoopTree node, final Void p) {
        final var header = "for (" + node.getVariable().getType() + " " + node.getVariable().getName()
            + " : " + node.getExpression() + ")";
        return buildBlock(header, blockStmts(node.getStatement()));
    }

    @Override
    public @Nullable Doc visitWhileLoop(final WhileLoopTree node, final Void p) {
        // javac wraps the condition in JCParens, so toString() already includes the outer ()
        return buildBlock("while " + node.getCondition(), blockStmts(node.getStatement()));
    }

    private List<Doc> blockStmts(final StatementTree stmt) {
        if (stmt instanceof BlockTree block) {
            return block.getStatements().stream()
                .flatMap(s -> Optional.ofNullable(scan(s, null)).stream())
                .toList();
        }
        return Optional.ofNullable(scan(stmt, null)).map(List::of).orElse(List.of());
    }

    private Doc buildBlock(final String header, final List<Doc> stmts) {
        if (stmts.isEmpty()) {
            return new Doc.Text(header + " {}");
        }
        return new Doc.Concat(Stream.concat(
            blockParts(header, stmts),
            Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("}"))
        ));
    }

    private Stream<Doc> blockParts(final String header, final List<Doc> stmts) {
        return Stream.concat(
            Stream.<Doc>of(new Doc.Text(header + " {")),
            stmts.stream()
                .<Doc>map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s))))
        );
    }

    private static String stripTrailingSemicolon(final String s) {
        return s.endsWith(";") ? s.substring(0, s.length() - 1) : s;
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
