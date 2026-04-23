package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;

import io.github.jschneidereit.grind.GrindConfig;
import io.github.jschneidereit.grind.parser.ParsedUnit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

public final class DocBuilder extends TreeScanner<@Nullable Doc, Void> {

    private final GrindConfig config;
    private final ParsedUnit unit;
    private final LeadingCommentAttacher attacher;

    private Recursor recursor() {
        return tree -> scan(tree, null);
    }

    public static Doc build(final CompilationUnitTree tree) {
        return build(tree, GrindConfig.defaults());
    }

    public static Doc build(final CompilationUnitTree tree, final GrindConfig config) {
        Objects.requireNonNull(tree, "tree");
        return build(emptyUnit(tree), config);
    }

    public static Doc build(final ParsedUnit unit) {
        return build(unit, GrindConfig.defaults());
    }

    public static Doc build(final ParsedUnit unit, final GrindConfig config) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(config, "config");
        final var doc = new DocBuilder(unit, config).visitCompilationUnit(unit.tree(), null);
        return Objects.requireNonNull(doc, "visitCompilationUnit returned null");
    }

    @Override
    public @Nullable Doc scan(final @Nullable Tree tree, final Void p) {
        if (tree == null) {
            return null;
        }
        final var result = isHandled(tree) ? super.scan(tree, null) : null;
        final var rendered = result != null ? result : textFallback(tree);
        return attacher.attach(tree, rendered);
    }

    @Override
    public @Nullable Doc reduce(final @Nullable Doc r1, final @Nullable Doc r2) {
        return null;
    }

    private static boolean isHandled(final Tree tree) {
        return tree instanceof CompilationUnitTree
            || tree instanceof ClassTree
            || tree instanceof MethodTree
            || tree instanceof VariableTree
            || tree instanceof ReturnTree
            || tree instanceof ExpressionStatementTree
            || tree instanceof MethodInvocationTree
            || tree instanceof LambdaExpressionTree
            || tree instanceof SwitchTree
            || tree instanceof SwitchExpressionTree
            || tree instanceof CaseTree
            || tree instanceof IfTree
            || tree instanceof ForLoopTree
            || tree instanceof EnhancedForLoopTree
            || tree instanceof WhileLoopTree
            || tree instanceof DoWhileLoopTree
            || tree instanceof ThrowTree
            || tree instanceof TryTree
            || tree instanceof NewArrayTree
            || tree instanceof TypeCastTree;
    }

    private static Doc textFallback(final Tree tree) {
        final var s = tree.toString();
        if (s.indexOf('\n') < 0 && s.indexOf('\r') < 0) {
            return new Doc.Text(s);
        }
        return new Doc.Concat(java.util.Arrays.stream(s.split("\n", -1))
            .map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
            .<Doc>map(Doc.Text::new)
            .flatMap(t -> Stream.<Doc>of(new Doc.HardLine(), t))
            .skip(1));
    }

    @Override
    public @Nullable Doc visitCompilationUnit(final CompilationUnitTree node, final Void p) {
        final var hasPackage = node.getPackageName() != null;
        final var pkgStream = hasPackage
            ? Stream.<Doc>of(new Doc.Text("package " + node.getPackageName() + ";"), new Doc.HardLine())
            : Stream.<Doc>empty();
        return new Doc.Concat(Stream.concat(
            CommentDocs.fileHeaderStream(unit.fileHeader()),
            Stream.concat(
                Stream.concat(
                    Stream.concat(pkgStream, ImportSectionRenderer.buildImportSection(hasPackage, node.getImports(), unit)),
                    node.getTypeDecls().stream()
                        .<Doc>flatMap(decl -> Optional.<Doc>ofNullable(scan(decl, null)).stream())),
                CommentDocs.fileFooterStream(unit.fileFooter()))));
    }

    @Override
    public @Nullable Doc visitClass(final ClassTree node, final Void p) {
        final var recursor = recursor();
        return switch (node.getKind()) {
            case RECORD -> RecordRenderer.render(node, recursor, config, attacher);
            case ENUM -> EnumRenderer.render(node, recursor, config, attacher);
            case INTERFACE -> ClassLikeRenderer.render(node, "interface", recursor, config, attacher);
            default -> ClassLikeRenderer.render(node, "class", recursor, config, attacher);
        };
    }

    @Override
    public @Nullable Doc visitVariable(final VariableTree node, final Void p) {
        return FieldRenderer.render(node, recursor());
    }

    @Override
    public @Nullable Doc visitMethod(final MethodTree node, final Void p) {
        return MethodRenderer.render(node, recursor(), attacher);
    }

    @Override
    public @Nullable Doc visitReturn(final ReturnTree node, final Void p) {
        return SimpleStatementRenderers.renderReturn(node, recursor());
    }

    @Override
    public @Nullable Doc visitExpressionStatement(final ExpressionStatementTree node, final Void p) {
        return SimpleStatementRenderers.renderExpressionStatement(node, recursor());
    }

    @Override
    public @Nullable Doc visitMethodInvocation(final MethodInvocationTree node, final Void p) {
        return MethodChainRenderer.render(node, recursor());
    }

    @Override
    public @Nullable Doc visitNewArray(final NewArrayTree node, final Void p) {
        if (node.getInitializers() == null || node.getInitializers().isEmpty()) {
            return new Doc.Text(node.toString());
        }
        final var elements = node.getInitializers().stream()
            .<Doc>map(this::scanOrText)
            .flatMap(d -> Stream.<Doc>of(new Doc.Text(", "), d))
            .skip(1);
        return new Doc.Concat(Stream.concat(
            Stream.concat(Stream.<Doc>of(new Doc.Text("{")), elements),
            Stream.<Doc>of(new Doc.Text("}"))));
    }

    @Override
    public @Nullable Doc visitTypeCast(final TypeCastTree node, final Void p) {
        return new Doc.Concat(List.of(
            new Doc.Text("("),
            scanOrText(node.getType()),
            new Doc.Text(") "),
            scanOrText(node.getExpression())));
    }

    private Doc scanOrText(final Tree tree) {
        return recursor().scanOrText(tree);
    }

    @Override
    public @Nullable Doc visitLambdaExpression(final LambdaExpressionTree node, final Void p) {
        return LambdaRenderer.render(node, recursor());
    }

    @Override
    public @Nullable Doc visitSwitch(final SwitchTree node, final Void p) {
        return SwitchExpressionRenderer.renderSwitch(node, recursor(), attacher);
    }

    @Override
    public @Nullable Doc visitSwitchExpression(final SwitchExpressionTree node, final Void p) {
        return SwitchExpressionRenderer.renderSwitch(node, recursor(), attacher);
    }

    @Override
    public @Nullable Doc visitCase(final CaseTree node, final Void p) {
        return SwitchExpressionRenderer.renderCase(node, recursor(), attacher);
    }

    @Override
    public @Nullable Doc visitIf(final IfTree node, final Void p) {
        return IfRenderer.render(node, recursor());
    }

    @Override
    public @Nullable Doc visitForLoop(final ForLoopTree node, final Void p) {
        return LoopRenderer.renderFor(node, recursor());
    }

    @Override
    public @Nullable Doc visitEnhancedForLoop(final EnhancedForLoopTree node, final Void p) {
        return LoopRenderer.renderEnhancedFor(node, recursor());
    }

    @Override
    public @Nullable Doc visitWhileLoop(final WhileLoopTree node, final Void p) {
        return LoopRenderer.renderWhile(node, recursor());
    }

    @Override
    public @Nullable Doc visitDoWhileLoop(final DoWhileLoopTree node, final Void p) {
        return LoopRenderer.renderDoWhile(node, recursor());
    }

    @Override
    public @Nullable Doc visitThrow(final ThrowTree node, final Void p) {
        return SimpleStatementRenderers.renderThrow(node);
    }

    @Override
    public @Nullable Doc visitTry(final TryTree node, final Void p) {
        return TryRenderer.render(node, recursor());
    }

    private DocBuilder(final ParsedUnit unit, final GrindConfig config) {
        this.unit = unit;
        this.config = config;
        this.attacher = new LeadingCommentAttacher() {
            @Override
            public Doc attach(final Tree node, final Doc doc) {
                final var withLeading = CommentDocs.prepend(unit.leadingOf(node), doc);
                return CommentDocs.appendTrailing(withLeading, unit.trailingOf(node));
            }

            @Override
            public java.util.List<io.github.jschneidereit.grind.parser.CommentToken> interior(final Tree node) {
                return unit.interiorOf(node);
            }
        };
    }

    private static ParsedUnit emptyUnit(final CompilationUnitTree tree) {
        return new ParsedUnit(tree, List.of(), List.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
    }
}
