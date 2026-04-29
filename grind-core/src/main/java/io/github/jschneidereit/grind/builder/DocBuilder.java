package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.IntersectionTypeTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.tree.YieldTree;
import com.sun.source.util.TreeScanner;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.GrindConfig;
import io.github.jschneidereit.grind.doc.Doc;
import io.github.jschneidereit.grind.doc.LeadingCommentAttacher;
import io.github.jschneidereit.grind.parser.CommentToken;
import io.github.jschneidereit.grind.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

public final class DocBuilder extends TreeScanner<@Nullable Doc, Void> {

    private final GrindConfig config;
    private final ParsedUnit unit;
    private final LeadingCommentAttacher attacher;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Recursor recursor = new Recursor() {
        @Override
        public Doc scan(final Tree node) {
            Objects.requireNonNull(node, "node");
            return Objects.requireNonNull(DocBuilder.this.scan(node, null));
        }

        @Override
        public @Nullable Doc scanNullable(final Tree node) {
            Objects.requireNonNull(node, "node");
            return DocBuilder.this.scan(node, null);
        }

        @Override
        public void emitWarning(final String message, final Tree at) {
            diagnostics.add(new Diagnostic.Warning(message, unit.positionOf(at)));
        }

        @Override
        public boolean isVarargs(final VariableTree param) {
            // javac models `T... x` as ArrayTypeTree(T) with the `...` flag on the parameter
            // symbol, but javac's source-position range for the type tree spans the original
            // user-written form — `T...` for varargs vs. `T[]` for a regular array — so the
            // suffix is decisive without needing the symbol model.
            if (!(param.getType() instanceof ArrayTypeTree)) {
                return false;
            }
            return unit.sourceOf(param.getType()).endsWith("...");
        }

        @Override
        public boolean isCompactConstructor(final MethodTree ctor) {
            // javac synthesizes parameter trees for a record's compact canonical constructor
            // by reusing the header components, whose source positions sit before the ctor
            // body. A regular constructor's parameters are within its own range. So a param
            // positioned before the ctor's start is decisive evidence of compact form.
            if (!ctor.getName().contentEquals("<init>")) {
                return false;
            }
            final var ctorStart = unit.sourcePositions().getStartPosition(unit.tree(), ctor);
            if (ctorStart < 0) {
                return false;
            }
            return ctor.getParameters().stream()
                .mapToLong(p -> unit.sourcePositions().getStartPosition(unit.tree(), p))
                .anyMatch(pos -> pos >= 0 && pos < ctorStart);
        }
    };

    DocBuilder(final ParsedUnit unit, final GrindConfig config) {
        this.unit = unit;
        this.config = config;
        this.attacher = new CommentAttacher(unit);
    }

    @Override
    public @Nullable Doc scan(final @Nullable Tree tree, final Void p) {
        if (tree == null) {
            return null;
        }
        return attacher.attach(tree, dispatch(tree));
    }

    @Override
    public @Nullable Doc reduce(final @Nullable Doc r1, final @Nullable Doc r2) {
        throw new IllegalStateException("unexpected tree merge: r1=" + r1 + " r2=" + r2);
    }

    @Override
    public @Nullable Doc visitCompilationUnit(final CompilationUnitTree node, final Void p) {
        final var hasPackage = node.getPackageName() != null;
        final var packageAnnotations = node.getPackageAnnotations().stream()
            .<Doc>flatMap(a -> Stream.of(new Doc.Text(a.toString()), new Doc.HardLine()));
        final var pkgStream = hasPackage
            ? Stream.<Doc>of(new Doc.Text("package " + node.getPackageName() + ";"), new Doc.HardLine())
            : Stream.<Doc>empty();
        return new Doc.Concat(Stream.concat(
            CommentDocs.fileHeaderStream(unit.fileHeader()),
            Stream.concat(
                Stream.concat(
                    Stream.concat(
                        Stream.concat(packageAnnotations, pkgStream),
                        ImportSectionRenderer.buildImportSection(hasPackage, node.getImports(), unit)),
                    node.getTypeDecls().stream()
                        .<Doc>flatMap(decl -> Optional.<Doc>ofNullable(scan(decl, null)).stream())),
                CommentDocs.fileFooterStream(unit.fileFooter()))));
    }

    @Override
    public @Nullable Doc visitClass(final ClassTree node, final Void p) {
        return switch (node.getKind()) {
            case RECORD -> RecordRenderer.render(node, recursor, config, attacher);
            case ENUM -> EnumRenderer.render(node, recursor, config, attacher);
            case INTERFACE -> ClassLikeRenderer.render(node, "interface", recursor, config, attacher);
            case ANNOTATION_TYPE -> ClassLikeRenderer.render(node, "@interface", recursor, config, attacher);
            case CLASS -> ClassLikeRenderer.render(node, "class", recursor, config, attacher);
            default -> throw new IllegalStateException("unexpected class kind: " + node.getKind());
        };
    }

    @Override
    public @Nullable Doc visitVariable(final VariableTree node, final Void p) {
        return FieldRenderer.render(node, recursor);
    }

    @Override
    public @Nullable Doc visitMethod(final MethodTree node, final Void p) {
        return MethodRenderer.render(node, recursor, attacher);
    }

    @Override
    public @Nullable Doc visitReturn(final ReturnTree node, final Void p) {
        return SimpleStatementRenderers.renderReturn(node, recursor);
    }

    @Override
    public @Nullable Doc visitExpressionStatement(final ExpressionStatementTree node, final Void p) {
        return SimpleStatementRenderers.renderExpressionStatement(node, recursor);
    }

    @Override
    public @Nullable Doc visitMethodInvocation(final MethodInvocationTree node, final Void p) {
        return MethodChainRenderer.render(node, recursor);
    }

    @Override
    public @Nullable Doc visitNewArray(final NewArrayTree node, final Void p) {
        return ExpressionRenderers.renderNewArray(node, recursor);
    }

    @Override
    public @Nullable Doc visitTypeCast(final TypeCastTree node, final Void p) {
        return ExpressionRenderers.renderTypeCast(node, recursor);
    }

    @Override
    public @Nullable Doc visitParenthesized(final ParenthesizedTree node, final Void p) {
        return ExpressionRenderers.renderParenthesized(node, recursor);
    }

    @Override
    public @Nullable Doc visitInstanceOf(final InstanceOfTree node, final Void p) {
        return ExpressionRenderers.renderInstanceOf(node, recursor);
    }

    @Override
    public @Nullable Doc visitBindingPattern(final BindingPatternTree node, final Void p) {
        return ExpressionRenderers.renderBindingPattern(node, recursor);
    }

    @Override
    public @Nullable Doc visitDeconstructionPattern(final DeconstructionPatternTree node, final Void p) {
        return ExpressionRenderers.renderDeconstructionPattern(node, recursor);
    }

    @Override
    public @Nullable Doc visitPatternCaseLabel(final PatternCaseLabelTree node, final Void p) {
        return ExpressionRenderers.renderPatternCaseLabel(node, recursor);
    }

    @Override
    public @Nullable Doc visitBinary(final BinaryTree node, final Void p) {
        return ExpressionRenderers.renderBinary(node, recursor);
    }

    @Override
    public @Nullable Doc visitUnary(final UnaryTree node, final Void p) {
        return ExpressionRenderers.renderUnary(node, recursor);
    }

    @Override
    public @Nullable Doc visitNewClass(final NewClassTree node, final Void p) {
        return ExpressionRenderers.renderNewClass(node, recursor);
    }

    @Override
    public @Nullable Doc visitAssignment(final AssignmentTree node, final Void p) {
        return ExpressionRenderers.renderAssignment(node, recursor);
    }

    @Override
    public @Nullable Doc visitCompoundAssignment(final CompoundAssignmentTree node, final Void p) {
        return ExpressionRenderers.renderCompoundAssignment(node, recursor);
    }

    @Override
    public @Nullable Doc visitMemberSelect(final MemberSelectTree node, final Void p) {
        return ExpressionRenderers.renderMemberSelect(node, recursor);
    }

    @Override
    public @Nullable Doc visitArrayAccess(final ArrayAccessTree node, final Void p) {
        return ExpressionRenderers.renderArrayAccess(node, recursor);
    }

    @Override
    public @Nullable Doc visitConditionalExpression(final ConditionalExpressionTree node, final Void p) {
        return ExpressionRenderers.renderConditionalExpression(node, recursor);
    }

    @Override
    public @Nullable Doc visitConstantCaseLabel(final ConstantCaseLabelTree node, final Void p) {
        return ExpressionRenderers.renderConstantCaseLabel(node, recursor);
    }

    @Override
    public @Nullable Doc visitMemberReference(final MemberReferenceTree node, final Void p) {
        return ExpressionRenderers.renderMemberReference(node, recursor);
    }

    @Override
    public @Nullable Doc visitAnnotation(final AnnotationTree node, final Void p) {
        return ExpressionRenderers.renderAnnotation(node, recursor);
    }

    @Override
    public @Nullable Doc visitLambdaExpression(final LambdaExpressionTree node, final Void p) {
        return LambdaRenderer.render(node, recursor);
    }

    @Override
    public @Nullable Doc visitSwitch(final SwitchTree node, final Void p) {
        return SwitchExpressionRenderer.renderSwitch(node, recursor);
    }

    @Override
    public @Nullable Doc visitSwitchExpression(final SwitchExpressionTree node, final Void p) {
        return SwitchExpressionRenderer.renderSwitch(node, recursor);
    }

    @Override
    public @Nullable Doc visitCase(final CaseTree node, final Void p) {
        return SwitchExpressionRenderer.renderCase(node, recursor, attacher);
    }

    @Override
    public @Nullable Doc visitIf(final IfTree node, final Void p) {
        return IfRenderer.render(node, recursor);
    }

    @Override
    public @Nullable Doc visitForLoop(final ForLoopTree node, final Void p) {
        return LoopRenderer.renderFor(node, recursor);
    }

    @Override
    public @Nullable Doc visitEnhancedForLoop(final EnhancedForLoopTree node, final Void p) {
        return LoopRenderer.renderEnhancedFor(node, recursor);
    }

    @Override
    public @Nullable Doc visitWhileLoop(final WhileLoopTree node, final Void p) {
        return LoopRenderer.renderWhile(node, recursor);
    }

    @Override
    public @Nullable Doc visitDoWhileLoop(final DoWhileLoopTree node, final Void p) {
        return LoopRenderer.renderDoWhile(node, recursor);
    }

    @Override
    public @Nullable Doc visitThrow(final ThrowTree node, final Void p) {
        return SimpleStatementRenderers.renderThrow(node, recursor);
    }

    @Override
    public @Nullable Doc visitTry(final TryTree node, final Void p) {
        return TryRenderer.render(node, recursor);
    }

    @Override
    public @Nullable Doc visitAssert(final AssertTree node, final Void p) {
        return SimpleStatementRenderers.renderAssert(node, recursor);
    }

    @Override
    public @Nullable Doc visitSynchronized(final SynchronizedTree node, final Void p) {
        return SimpleStatementRenderers.renderSynchronized(node, recursor);
    }

    @Override
    public @Nullable Doc visitLabeledStatement(final LabeledStatementTree node, final Void p) {
        return SimpleStatementRenderers.renderLabeled(node, recursor);
    }

    @Override
    public @Nullable Doc visitYield(final YieldTree node, final Void p) {
        return SimpleStatementRenderers.renderYield(node, recursor);
    }

    @Override
    public @Nullable Doc visitIdentifier(final IdentifierTree node, final Void p) {
        return TypeRenderers.renderIdentifier(node);
    }

    @Override
    public @Nullable Doc visitPrimitiveType(final PrimitiveTypeTree node, final Void p) {
        return TypeRenderers.renderPrimitiveType(node);
    }

    @Override
    public @Nullable Doc visitLiteral(final LiteralTree node, final Void p) {
        return TypeRenderers.renderLiteral(node, unit);
    }

    @Override
    public @Nullable Doc visitEmptyStatement(final EmptyStatementTree node, final Void p) {
        return TypeRenderers.renderEmptyStatement(node);
    }

    @Override
    public @Nullable Doc visitBreak(final BreakTree node, final Void p) {
        return TypeRenderers.renderBreak(node);
    }

    @Override
    public @Nullable Doc visitContinue(final ContinueTree node, final Void p) {
        return TypeRenderers.renderContinue(node);
    }

    @Override
    public @Nullable Doc visitDefaultCaseLabel(final DefaultCaseLabelTree node, final Void p) {
        return TypeRenderers.renderDefaultCaseLabel(node);
    }

    @Override
    public @Nullable Doc visitArrayType(final ArrayTypeTree node, final Void p) {
        return TypeRenderers.renderArrayType(node, recursor);
    }

    @Override
    public @Nullable Doc visitParameterizedType(final ParameterizedTypeTree node, final Void p) {
        return TypeRenderers.renderParameterizedType(node, recursor);
    }

    @Override
    public @Nullable Doc visitUnionType(final UnionTypeTree node, final Void p) {
        return TypeRenderers.renderUnionType(node, recursor);
    }

    @Override
    public @Nullable Doc visitIntersectionType(final IntersectionTypeTree node, final Void p) {
        return TypeRenderers.renderIntersectionType(node, recursor);
    }

    @Override
    public @Nullable Doc visitWildcard(final WildcardTree node, final Void p) {
        return TypeRenderers.renderWildcard(node, recursor);
    }

    @Override
    public @Nullable Doc visitTypeParameter(final TypeParameterTree node, final Void p) {
        return TypeRenderers.renderTypeParameter(node, recursor);
    }

    @Override
    public @Nullable Doc visitAnnotatedType(final AnnotatedTypeTree node, final Void p) {
        return TypeRenderers.renderAnnotatedType(node, recursor);
    }

    @Override
    public @Nullable Doc visitBlock(final BlockTree node, final Void p) {
        final var stmts = node.getStatements().stream()
            .<Doc>flatMap(s -> Optional.ofNullable(scan(s, null)).stream())
            .toList();
        return BlockRenderer.buildBlock(stmts, attacher.interior(node));
    }

    @Override
    public @Nullable Doc visitCatch(final CatchTree node, final Void p) {
        final var param = node.getParameter();
        final var header = new Doc.Concat(List.of(
            new Doc.Text("catch ("),
            recursor.scan(param.getType()),
            new Doc.Text(" " + param.getName() + ")")));
        final var stmts = BlockRenderer.blockStmts(node.getBlock(), recursor);
        return BlockRenderer.buildBlock(header, stmts, attacher.interior(node.getBlock()));
    }

    @Override
    public @Nullable Doc visitModifiers(final ModifiersTree node, final Void p) {
        final var sb = new StringBuilder();
        ModifierRenderer.renderAnnotations(node, sb);
        ModifierRenderer.renderModifiers(node, sb);
        final var text = sb.toString().stripTrailing();
        return new Doc.Text(text);
    }

    private Doc dispatch(final Tree tree) {
        final var result = super.scan(tree, null);
        if (result == null) {
            throw new IllegalStateException(unhandledMessage(tree.getKind(), tree));
        }
        return result;
    }

    private static String unhandledMessage(final Tree.Kind kind, final Tree tree) {
        return switch (kind) {
            case MODULE, EXPORTS, OPENS, PROVIDES, REQUIRES, USES ->
                "modules not yet supported: " + kind;
            default -> "no renderer for " + kind + ": " + tree;
        };
    }

    public static Doc build(final ParsedUnit unit) {
        return build(unit, GrindConfig.defaults());
    }

    public static Doc build(final ParsedUnit unit, final GrindConfig config) {
        return buildWithFallbacks(unit, config).doc();
    }

    public static BuildResult buildWithFallbacks(final ParsedUnit unit, final GrindConfig config) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(config, "config");
        final var builder = new DocBuilder(unit, config);
        final var doc = builder.visitCompilationUnit(unit.tree(), null);
        return new BuildResult(
            Objects.requireNonNull(doc, "visitCompilationUnit returned null"),
            List.copyOf(builder.diagnostics));
    }

    public record BuildResult(Doc doc, List<Diagnostic> diagnostics) {}

    private record CommentAttacher(ParsedUnit unit) implements LeadingCommentAttacher {

        @Override
        public Doc attach(final Tree node, final Doc doc) {
            final var withLeading = CommentDocs.prepend(unit.leadingOf(node), doc);
            return CommentDocs.appendTrailing(withLeading, unit.trailingOf(node));
        }

        @Override
        public List<CommentToken> interior(final Tree node) {
            return unit.interiorOf(node);
        }

        @Override
        public List<CommentToken> tail(final Tree node) {
            return unit.tailOf(node);
        }
    }
}
