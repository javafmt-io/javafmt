package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreeScanner;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

public final class DocBuilder extends TreeScanner<@Nullable Doc, Void> {

    private Recursor recursor() {
        return tree -> scan(tree, null);
    }

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
            Stream.concat(pkgStream, ImportSectionRenderer.buildImportSection(hasPackage, node.getImports())),
            node.getTypeDecls().stream()
                .flatMap(decl -> Optional.ofNullable(scan(decl, null)).stream())
        ));
    }

    @Override
    public @Nullable Doc visitClass(final ClassTree node, final Void p) {
        final var recursor = recursor();
        return switch (node.getKind()) {
            case RECORD -> RecordRenderer.render(node, recursor);
            case ENUM -> EnumRenderer.render(node, recursor);
            case INTERFACE -> ClassLikeRenderer.render(node, "interface", recursor);
            default -> ClassLikeRenderer.render(node, "class", recursor);
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

    private DocBuilder() {}

}
