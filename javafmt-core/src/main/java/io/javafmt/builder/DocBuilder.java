package io.javafmt.builder;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AnyPatternTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BindingPatternTree;
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
import com.sun.source.tree.ModuleTree;
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
import io.javafmt.Diagnostic;
import io.javafmt.JavafmtConfig;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import io.javafmt.parser.CommentToken;
import io.javafmt.parser.ParsedUnit;
import io.javafmt.parser.TreeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class DocBuilder {

    private final ParsedUnit unit;

    private final TreeFacts facts;

    private final LeadingCommentAttacher attacher;

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private int compactDepth;

    private final Recursor recursor = new Recursor() {

        @Override
        public Doc scan(final Tree node) {
            Objects.requireNonNull(node, "node");
            return Objects.requireNonNull(DocBuilder.this.scan(node));
        }

        @Override
        public Doc scanCompact(final Tree node) {
            Objects.requireNonNull(node, "node");
            compactDepth++;
            try {
                return Objects.requireNonNull(DocBuilder.this.scan(node));
            } finally {
                compactDepth--;
            }
        }

        @Override
        public boolean inCompactContext() {
            return compactDepth > 0;
        }

        @Override
        @Nullable
        public Doc scanNullable(final Tree node) {
            Objects.requireNonNull(node, "node");
            return DocBuilder.this.scan(node);
        }

        @Override
        public Doc withComments(final Tree node, final Doc rendered) {
            return attacher.attach(node, rendered);
        }

        @Override
        public void emitWarning(final String message, final Tree at) {
            diagnostics.add(new Diagnostic.Warning(message, unit.positionOf(at)));
        }

        @Override
        public boolean isVarargs(final VariableTree param) {
            return facts.isVarargs(param);
        }

        @Override
        public String sourceOf(final Tree node) {
            return unit.sourceOf(node);
        }

        @Override
        public boolean isFollowedByNewline(final Tree node) {
            return facts.isFollowedByNewline(node);
        }

        @Override
        public boolean blankLineBetween(final Tree before, final Tree after) {
            return facts.blankLineBetween(before, after);
        }

        @Override
        public boolean hasJavadoc(final Tree node) {
            return facts.hasJavadoc(node);
        }

        @Override
        public boolean isCompactConstructor(final MethodTree ctor) {
            return facts.isCompactConstructor(ctor);
        }

        @Override
        public boolean isEnumConstant(final VariableTree member) {
            return facts.isEnumConstant(member);
        }
    };

    DocBuilder(final ParsedUnit unit) {
        this.unit = unit;
        this.facts = new TreeFacts(unit);
        this.attacher = new CommentAttacher(unit);
    }

    @Nullable
    private Doc scan(@Nullable final Tree tree) {
        if (tree == null) {
            return null;
        }
        return attacher.attach(tree, dispatch(tree));
    }

    private Doc visitCompilationUnit(final CompilationUnitTree node) {
        final var hasPackage = node.getPackageName() != null;
        final var packageAnnotations = node.getPackageAnnotations()
                .stream()
                .<Doc>flatMap(a -> Stream.of(new Doc.Text(a.toString()), Doc.HARD_LINE));
        // The blank line after the package declaration normally comes from the import section. With
        // no imports there is no import section, and the first type ran straight into the package.
        final var packageSeparator = node.getImports().isEmpty() ? Stream.<Doc>of(Doc.HARD_LINE) : Stream.<Doc>empty();
        final var pkgStream = hasPackage
                ? Stream.concat(Stream.<Doc>of(new Doc.Text("package " + node.getPackageName() + ";"), Doc.HARD_LINE), packageSeparator)
                : Stream.<Doc>empty();
        final var module = node.getModule();
        final var moduleStream = module == null ? Stream.<Doc>empty() : Optional.<Doc>ofNullable(scan(module)).stream();
        final var typeDeclsStream = Doc.intersperse(
                new Doc.Concat(List.of(Doc.HARD_LINE, Doc.HARD_LINE)),
                node.getTypeDecls()
                        .stream()
                        .<Doc>flatMap(decl -> Optional.<Doc>ofNullable(scan(decl)).stream()));
        return Doc.concatOf(
                Stream.of(
                        CommentDocs.fileHeaderStream(unit.fileHeader()),
                        packageAnnotations,
                        pkgStream,
                        ImportSectionRenderer.buildImportSection(hasPackage, node.getImports(), unit),
                        moduleStream,
                        typeDeclsStream,
                        CommentDocs.fileFooterStream(unit.fileFooter())));
    }

    private Doc visitClass(final ClassTree node) {
        return switch (node.getKind()) {
            case RECORD -> RecordRenderer.render(node, recursor, attacher);
            case ENUM -> EnumRenderer.render(node, recursor, attacher);
            case INTERFACE -> ClassLikeRenderer.render(node, "interface", recursor, attacher);
            case ANNOTATION_TYPE -> ClassLikeRenderer.render(node, "@interface", recursor, attacher);
            case CLASS ->
                isImplicitClassDeclaration(node)
                        ? ClassLikeRenderer.renderImplicit(node, recursor, attacher)
                        : ClassLikeRenderer.render(node, "class", recursor, attacher);
            default -> throw new IllegalStateException("unexpected class kind: " + node.getKind());
        };
    }

    /**
     * Whether {@code node} is a Java 25 compact source file's implicit class: one with no
     * explicit class declaration and therefore no closing brace, so javac assigns it no end
     * position.
     */
    private boolean isImplicitClassDeclaration(final ClassTree node) {
        return unit.sourcePositions().getEndPosition(unit.tree(), node) < 0;
    }

    private Doc visitBlock(final BlockTree node) {
        final var stmts = node.getStatements()
                .stream()
                .<Doc>flatMap(s -> Optional.ofNullable(scan(s)).stream())
                .toList();
        return BlockRenderer.buildBlock(stmts, attacher.trailingInside(node));
    }

    /**
     * Renders a catch clause standing on its own, closing its own block. Every catch reachable
     * from a {@code try} is instead rendered by {@link TryRenderer}, which chains the braces
     * across the whole statement; this override covers a {@link CatchTree} arriving through a
     * plain {@code scan}. The clause text comes from {@link TryRenderer#catchClause} so the two
     * cannot drift apart.
     */
    private Doc visitCatch(final CatchTree node) {
        final var stmts = BlockRenderer.blockStmts(node.getBlock(), recursor, attacher);
        return BlockRenderer.buildBlock(TryRenderer.catchClause(node, recursor), stmts, List.of());
    }

    private Doc visitModifiers(final ModifiersTree node) {
        final var text = ModifierRenderer.prefixOf(node, recursor).stripTrailing();
        return new Doc.Text(text);
    }

    private Doc dispatch(final Tree tree) {
        final var result = switch (tree) {
            case CompilationUnitTree n -> visitCompilationUnit(n);
            case ModuleTree n -> ModuleRenderer.render(n, attacher, recursor);
            case ClassTree n -> visitClass(n);
            case VariableTree n -> FieldRenderer.render(n, recursor);
            case MethodTree n -> MethodRenderer.render(n, recursor, attacher);
            case ReturnTree n -> SimpleStatementRenderers.renderReturn(n, recursor);
            case ExpressionStatementTree n -> SimpleStatementRenderers.renderExpressionStatement(n, recursor);
            case MethodInvocationTree n -> MethodChainRenderer.render(n, recursor, attacher);
            case NewArrayTree n -> ExpressionRenderers.renderNewArray(n, recursor);
            case TypeCastTree n -> ExpressionRenderers.renderTypeCast(n, recursor);
            case ParenthesizedTree n -> ExpressionRenderers.renderParenthesized(n, recursor);
            case InstanceOfTree n -> ExpressionRenderers.renderInstanceOf(n, recursor);
            case BindingPatternTree n -> ExpressionRenderers.renderBindingPattern(n, recursor);
            case DeconstructionPatternTree n -> ExpressionRenderers.renderDeconstructionPattern(n, recursor);
            case PatternCaseLabelTree n -> ExpressionRenderers.renderPatternCaseLabel(n, recursor);
            case BinaryTree n -> ExpressionRenderers.renderBinary(n, recursor);
            case UnaryTree n -> ExpressionRenderers.renderUnary(n, recursor);
            case NewClassTree n -> ExpressionRenderers.renderNewClass(n, recursor);
            case AssignmentTree n -> ExpressionRenderers.renderAssignment(n, recursor);
            case CompoundAssignmentTree n -> ExpressionRenderers.renderCompoundAssignment(n, recursor);
            case MemberSelectTree n -> ExpressionRenderers.renderMemberSelect(n, recursor);
            case ArrayAccessTree n -> ExpressionRenderers.renderArrayAccess(n, recursor);
            case ConditionalExpressionTree n -> ExpressionRenderers.renderConditionalExpression(n, recursor);
            case ConstantCaseLabelTree n -> ExpressionRenderers.renderConstantCaseLabel(n, recursor);
            case MemberReferenceTree n -> ExpressionRenderers.renderMemberReference(n, recursor);
            case AnnotationTree n -> ExpressionRenderers.renderAnnotation(n, recursor);
            case LambdaExpressionTree n -> LambdaRenderer.render(n, recursor, attacher);
            case SwitchTree n -> SwitchExpressionRenderer.renderSwitch(n, recursor);
            case SwitchExpressionTree n -> SwitchExpressionRenderer.renderSwitch(n, recursor);
            case CaseTree n -> SwitchExpressionRenderer.renderCase(n, recursor, attacher);
            case IfTree n -> IfRenderer.render(n, recursor, attacher);
            case ForLoopTree n -> LoopRenderer.renderFor(n, recursor, attacher);
            case EnhancedForLoopTree n -> LoopRenderer.renderEnhancedFor(n, recursor, attacher);
            case WhileLoopTree n -> LoopRenderer.renderWhile(n, recursor, attacher);
            case DoWhileLoopTree n -> LoopRenderer.renderDoWhile(n, recursor, attacher);
            case ThrowTree n -> SimpleStatementRenderers.renderThrow(n, recursor);
            case TryTree n -> TryRenderer.render(n, recursor, attacher);
            case AssertTree n -> SimpleStatementRenderers.renderAssert(n, recursor);
            case SynchronizedTree n -> SimpleStatementRenderers.renderSynchronized(n, recursor, attacher);
            case LabeledStatementTree n -> SimpleStatementRenderers.renderLabeled(n, recursor);
            case YieldTree n -> SimpleStatementRenderers.renderYield(n, recursor);
            case IdentifierTree n -> TypeRenderers.renderIdentifier(n);
            case PrimitiveTypeTree n -> TypeRenderers.renderPrimitiveType(n);
            case LiteralTree n -> TypeRenderers.renderLiteral(n, unit);
            case AnyPatternTree n -> TypeRenderers.renderAnyPattern(n);
            case EmptyStatementTree n -> TypeRenderers.renderEmptyStatement(n);
            case BreakTree n -> TypeRenderers.renderBreak(n);
            case ContinueTree n -> TypeRenderers.renderContinue(n);
            case DefaultCaseLabelTree n -> TypeRenderers.renderDefaultCaseLabel(n);
            case ArrayTypeTree n -> TypeRenderers.renderArrayType(n, recursor);
            case ParameterizedTypeTree n -> TypeRenderers.renderParameterizedType(n, recursor);
            case UnionTypeTree n -> TypeRenderers.renderUnionType(n, recursor);
            case IntersectionTypeTree n -> TypeRenderers.renderIntersectionType(n, recursor);
            case WildcardTree n -> TypeRenderers.renderWildcard(n, recursor);
            case TypeParameterTree n -> TypeRenderers.renderTypeParameter(n, recursor);
            case AnnotatedTypeTree n -> TypeRenderers.renderAnnotatedType(n, recursor);
            case BlockTree n -> visitBlock(n);
            case CatchTree n -> visitCatch(n);
            case ModifiersTree n -> visitModifiers(n);
            default -> throw new IllegalStateException("no renderer for " + tree.getKind() + ": " + tree);
        };
        return Objects.requireNonNull(result, () -> "renderer returned null for " + tree.getKind());
    }

    public static Doc build(final ParsedUnit unit) {
        return build(unit, JavafmtConfig.defaults());
    }

    public static Doc build(final ParsedUnit unit, final JavafmtConfig config) {
        return buildWithFallbacks(unit, config).doc();
    }

    /**
     * Builds the {@link Doc} tree for {@code unit}, plus any warnings raised while doing so.
     * {@code config} threads through this signature to stay part of javafmt's caller-facing
     * contract, even though the builder itself has no config-dependent rendering left since
     * member reordering was removed.
     */
    public static BuildResult buildWithFallbacks(final ParsedUnit unit, final JavafmtConfig config) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(config, "config");
        final var builder = new DocBuilder(unit);
        final var doc = builder.visitCompilationUnit(unit.tree());
        return new BuildResult(Objects.requireNonNull(doc, "visitCompilationUnit returned null"), List.copyOf(builder.diagnostics));
    }

    public record BuildResult(Doc doc, List<Diagnostic> diagnostics) {}

    private record CommentAttacher(ParsedUnit unit) implements LeadingCommentAttacher {

        @Override
        public Doc attach(final Tree node, final Doc doc) {
            final var leading = unit.leadingOf(node);
            final var inlineFrom = firstInlineComment(node, leading);
            final var withLeading = CommentDocs.prepend(leading.subList(0, inlineFrom), leading.subList(inlineFrom, leading.size()), doc);
            return CommentDocs.appendTrailing(withLeading, unit.trailingOf(node));
        }

        /**
         * The index of the first leading comment the author wrote on {@code node}'s own line —
         * everything from there to the node is separated by spaces alone. Those comments are
         * rendered inline; the ones before them still take a line each.
         */
        private int firstInlineComment(final Tree node, final List<CommentToken> leading) {
            final var nodeStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), node);
            if (leading.isEmpty() || nodeStart < 0) {
                return leading.size();
            }
            var next = nodeStart;
            var index = leading.size();
            while (index > 0 && spacesOnly(leading.get(index - 1).end(), next)) {
                index--;
                next = leading.get(index).start();
            }
            return index;
        }

        private boolean spacesOnly(final int from, final int to) {
            return from >= 0
                && to <= unit.source().length()
                && from <= to
                && unit.source().substring(from, to).chars().allMatch(c -> c == ' ' || c == '\t');
        }

        @Override
        public List<CommentToken> interior(final Tree node) {
            return unit.interiorOf(node);
        }

        @Override
        public List<CommentToken> trailing(final Tree node) {
            return unit.trailingOf(node);
        }

        @Override
        public List<CommentToken> tail(final Tree node) {
            return unit.tailOf(node);
        }
    }
}
