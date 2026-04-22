package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreeScanner;

import io.github.jschneidereit.grind.GrindConfig;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

public final class DocBuilder extends TreeScanner<@Nullable Doc, Void> {

    private final GrindConfig config;

    private Recursor recursor() {
        return tree -> scan(tree, null);
    }

    public static Doc build(final CompilationUnitTree tree) {
        return build(tree, GrindConfig.defaults());
    }

    public static Doc build(final CompilationUnitTree tree, final GrindConfig config) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(config, "config");
        final var doc = new DocBuilder(config).visitCompilationUnit(tree, null);
        return Objects.requireNonNull(doc, "visitCompilationUnit returned null");
    }

    @Override
    public @Nullable Doc visitCompilationUnit(final CompilationUnitTree node, final Void p) {
        final var hasPackage = node.getPackageName() != null;
        final var pkgStream = hasPackage
            ? Stream.<Doc>of(new Doc.Text("package " + node.getPackageName() + ";"), new Doc.HardLine())
            : Stream.<Doc>empty();
        return new Doc.Concat(Stream.concat(
            Stream.concat(pkgStream, ImportSectionRenderer.buildImportSection(hasPackage, node.getImports())),
            node.getTypeDecls().stream()
                .flatMap(decl -> Optional.ofNullable(scan(decl, null)).stream())
        ));
    }

    @Override
    public @Nullable Doc visitClass(final ClassTree node, final Void p) {
        final var recursor = recursor();
        return switch (node.getKind()) {
            case RECORD -> RecordRenderer.render(node, recursor, config);
            case ENUM -> EnumRenderer.render(node, recursor, config);
            case INTERFACE -> ClassLikeRenderer.render(node, "interface", recursor, config);
            default -> ClassLikeRenderer.render(node, "class", recursor, config);
        };
    }

    @Override
    public @Nullable Doc visitVariable(final VariableTree node, final Void p) {
        return FieldRenderer.render(node);
    }

    @Override
    public @Nullable Doc visitMethod(final MethodTree node, final Void p) {
        return MethodRenderer.render(node, recursor());
    }

    @Override
    public @Nullable Doc visitReturn(final ReturnTree node, final Void p) {
        return SimpleStatementRenderers.renderReturn(node, recursor());
    }

    @Override
    public @Nullable Doc visitExpressionStatement(final ExpressionStatementTree node, final Void p) {
        return SimpleStatementRenderers.renderExpressionStatement(node);
    }

    @Override
    public @Nullable Doc visitSwitch(final SwitchTree node, final Void p) {
        return SwitchExpressionRenderer.renderSwitch(node, recursor());
    }

    @Override
    public @Nullable Doc visitSwitchExpression(final SwitchExpressionTree node, final Void p) {
        return SwitchExpressionRenderer.renderSwitch(node, recursor());
    }

    @Override
    public @Nullable Doc visitCase(final CaseTree node, final Void p) {
        return SwitchExpressionRenderer.renderCase(node, recursor());
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

    private DocBuilder(final GrindConfig config) {
        this.config = config;
    }

}
