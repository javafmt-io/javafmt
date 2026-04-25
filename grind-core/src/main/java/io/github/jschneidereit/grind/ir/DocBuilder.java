package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.GrindConfig;
import io.github.jschneidereit.grind.parser.CommentToken;
import io.github.jschneidereit.grind.parser.ParsedUnit;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

public final class DocBuilder extends TreeScanner<@Nullable Doc, Void> {

    private final GrindConfig config;
    private final ParsedUnit unit;
    private final LeadingCommentAttacher attacher;
    private final java.util.List<Diagnostic> diagnostics = new java.util.ArrayList<>();

    private Recursor recursor() {
        return new Recursor() {
            @Override
            public Doc scan(final Tree node) {
                Objects.requireNonNull(node, "node");
                return Objects.requireNonNull(DocBuilder.this.scan(node, null));
            }

            @Override
            public void emitWarning(final String message, final Tree at) {
                diagnostics.add(new Diagnostic.Warning(message, unit.positionOf(at)));
            }
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

    @Override
    public @Nullable Doc scan(final @Nullable Tree tree, final Void p) {
        if (tree == null) {
            return null;
        }
        return attacher.attach(tree, dispatch(tree));
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

    @Override
    public @Nullable Doc reduce(final @Nullable Doc r1, final @Nullable Doc r2) {
        throw new AssertionError("unexpected tree merge: r1=" + r1 + " r2=" + r2);
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
        final var type = node.getType();
        final var dimensions = node.getDimensions();
        final var initializers = node.getInitializers();
        final var prefix = type == null
            ? Stream.<Doc>empty()
            : Stream.<Doc>of(new Doc.Text("new "), scanOrText(type));
        final var dims = dimensions.stream()
            .<Doc>flatMap(d -> Stream.<Doc>of(new Doc.Text("["), scanOrText(d), new Doc.Text("]")));
        if (initializers == null) {
            return new Doc.Concat(Stream.concat(prefix, dims));
        }
        final var brace = type == null ? "{" : " {";
        if (initializers.isEmpty()) {
            return new Doc.Concat(Stream.concat(
                Stream.concat(prefix, dims),
                Stream.<Doc>of(new Doc.Text(brace + "}"))));
        }
        final var elements = Doc.intersperse(new Doc.Text(", "), initializers.stream()
            .<Doc>map(this::scanOrText));
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.concat(prefix, dims),
                Stream.concat(Stream.<Doc>of(new Doc.Text(brace)), elements)),
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

    @Override
    public @Nullable Doc visitParenthesized(final ParenthesizedTree node, final Void p) {
        return new Doc.Concat(List.of(
            new Doc.Text("("),
            scanOrText(node.getExpression()),
            new Doc.Text(")")));
    }

    @Override
    public @Nullable Doc visitInstanceOf(final InstanceOfTree node, final Void p) {
        final var pattern = node.getPattern();
        final var rhs = pattern != null ? scanOrText(pattern) : scanOrText(node.getType());
        return new Doc.Concat(List.of(
            scanOrText(node.getExpression()),
            new Doc.Text(" instanceof "),
            rhs));
    }

    @Override
    public @Nullable Doc visitBindingPattern(final BindingPatternTree node, final Void p) {
        final var variable = node.getVariable();
        final var typeDoc = variable.getType() == null ? new Doc.Text("var") : scanOrText(variable.getType());
        return new Doc.Concat(List.of(typeDoc, new Doc.Text(" " + variable.getName())));
    }

    @Override
    public @Nullable Doc visitDeconstructionPattern(final DeconstructionPatternTree node, final Void p) {
        final var nested = Doc.intersperse(new Doc.Text(", "), node.getNestedPatterns().stream()
            .<Doc>map(this::scanOrText));
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.<Doc>of(scanOrText(node.getDeconstructor()), new Doc.Text("(")),
                nested),
            Stream.<Doc>of(new Doc.Text(")"))));
    }

    @Override
    public @Nullable Doc visitPatternCaseLabel(final PatternCaseLabelTree node, final Void p) {
        return scanOrText(node.getPattern());
    }

    @Override
    public @Nullable Doc visitBinary(final BinaryTree node, final Void p) {
        return new Doc.Concat(List.of(
            scanOrText(node.getLeftOperand()),
            new Doc.Text(" " + operatorText(node) + " "),
            scanOrText(node.getRightOperand())));
    }

    @Override
    public @Nullable Doc visitUnary(final UnaryTree node, final Void p) {
        final var op = unaryOperatorText(node.getKind());
        final var postfix = node.getKind() == Tree.Kind.POSTFIX_INCREMENT || node.getKind() == Tree.Kind.POSTFIX_DECREMENT;
        return postfix
            ? new Doc.Concat(List.of(scanOrText(node.getExpression()), new Doc.Text(op)))
            : new Doc.Concat(List.of(new Doc.Text(op), scanOrText(node.getExpression())));
    }

    @Override
    public @Nullable Doc visitNewClass(final NewClassTree node, final Void p) {
        final var args = node.getArguments();
        final var argsDoc = args.isEmpty()
            ? (Doc) new Doc.Text("()")
            : new Doc.Concat(Stream.concat(
                Stream.concat(
                    Stream.<Doc>of(new Doc.Text("(")),
                    Doc.intersperse(new Doc.Text(", "), args.stream().<Doc>map(this::scanOrText))),
                Stream.<Doc>of(new Doc.Text(")"))));
        final var body = node.getClassBody();
        final var headParts = List.<Doc>of(new Doc.Text("new "), scanOrText(node.getIdentifier()), argsDoc);
        if (body == null) {
            return new Doc.Concat(headParts);
        }
        final var members = body.getMembers().stream()
            .<Doc>flatMap(m -> Optional.ofNullable(scan(m, null)).stream())
            .<Doc>map(d -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), new Doc.HardLine(), d))))
            .toList();
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                headParts.stream(),
                Stream.<Doc>of(new Doc.Text(" {"))),
            Stream.concat(
                members.stream(),
                Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("}")))));
    }

    @Override
    public @Nullable Doc visitAssignment(final AssignmentTree node, final Void p) {
        return new Doc.Concat(List.of(
            scanOrText(node.getVariable()),
            new Doc.Text(" = "),
            scanOrText(node.getExpression())));
    }

    @Override
    public @Nullable Doc visitCompoundAssignment(final CompoundAssignmentTree node, final Void p) {
        return new Doc.Concat(List.of(
            scanOrText(node.getVariable()),
            new Doc.Text(" " + compoundOperatorText(node.getKind()) + " "),
            scanOrText(node.getExpression())));
    }

    @Override
    public @Nullable Doc visitMemberSelect(final MemberSelectTree node, final Void p) {
        return new Doc.Concat(List.of(
            scanOrText(node.getExpression()),
            new Doc.Text("." + node.getIdentifier())));
    }

    @Override
    public @Nullable Doc visitArrayAccess(final ArrayAccessTree node, final Void p) {
        return new Doc.Concat(List.of(
            scanOrText(node.getExpression()),
            new Doc.Text("["),
            scanOrText(node.getIndex()),
            new Doc.Text("]")));
    }

    private static String compoundOperatorText(final Tree.Kind kind) {
        return switch (kind) {
            case MULTIPLY_ASSIGNMENT -> "*=";
            case DIVIDE_ASSIGNMENT -> "/=";
            case REMAINDER_ASSIGNMENT -> "%=";
            case PLUS_ASSIGNMENT -> "+=";
            case MINUS_ASSIGNMENT -> "-=";
            case LEFT_SHIFT_ASSIGNMENT -> "<<=";
            case RIGHT_SHIFT_ASSIGNMENT -> ">>=";
            case UNSIGNED_RIGHT_SHIFT_ASSIGNMENT -> ">>>=";
            case AND_ASSIGNMENT -> "&=";
            case XOR_ASSIGNMENT -> "^=";
            case OR_ASSIGNMENT -> "|=";
            default -> throw new IllegalStateException("Unexpected compound assignment: " + kind);
        };
    }

    @Override
    public @Nullable Doc visitConditionalExpression(final ConditionalExpressionTree node, final Void p) {
        return new Doc.Concat(List.of(
            scanOrText(node.getCondition()),
            new Doc.Text(" ? "),
            scanOrText(node.getTrueExpression()),
            new Doc.Text(" : "),
            scanOrText(node.getFalseExpression())));
    }

    private static String operatorText(final BinaryTree node) {
        return switch (node.getKind()) {
            case MULTIPLY -> "*";
            case DIVIDE -> "/";
            case REMAINDER -> "%";
            case PLUS -> "+";
            case MINUS -> "-";
            case LEFT_SHIFT -> "<<";
            case RIGHT_SHIFT -> ">>";
            case UNSIGNED_RIGHT_SHIFT -> ">>>";
            case LESS_THAN -> "<";
            case GREATER_THAN -> ">";
            case LESS_THAN_EQUAL -> "<=";
            case GREATER_THAN_EQUAL -> ">=";
            case EQUAL_TO -> "==";
            case NOT_EQUAL_TO -> "!=";
            case AND -> "&";
            case XOR -> "^";
            case OR -> "|";
            case CONDITIONAL_AND -> "&&";
            case CONDITIONAL_OR -> "||";
            default -> throw new IllegalStateException("Unexpected binary operator: " + node.getKind());
        };
    }

    private static String unaryOperatorText(final Tree.Kind kind) {
        return switch (kind) {
            case POSTFIX_INCREMENT, PREFIX_INCREMENT -> "++";
            case POSTFIX_DECREMENT, PREFIX_DECREMENT -> "--";
            case UNARY_PLUS -> "+";
            case UNARY_MINUS -> "-";
            case BITWISE_COMPLEMENT -> "~";
            case LOGICAL_COMPLEMENT -> "!";
            default -> throw new IllegalStateException("Unexpected unary operator: " + kind);
        };
    }

    private Doc scanOrText(final Tree tree) {
        return recursor().scan(tree);
    }

    @Override
    public @Nullable Doc visitLambdaExpression(final LambdaExpressionTree node, final Void p) {
        return LambdaRenderer.render(node, recursor());
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
        return SimpleStatementRenderers.renderThrow(node, recursor());
    }

    @Override
    public @Nullable Doc visitTry(final TryTree node, final Void p) {
        return TryRenderer.render(node, recursor());
    }

    @Override
    public @Nullable Doc visitAssert(final AssertTree node, final Void p) {
        return SimpleStatementRenderers.renderAssert(node, recursor());
    }

    @Override
    public @Nullable Doc visitSynchronized(final SynchronizedTree node, final Void p) {
        return SimpleStatementRenderers.renderSynchronized(node, recursor());
    }

    @Override
    public @Nullable Doc visitLabeledStatement(final LabeledStatementTree node, final Void p) {
        return SimpleStatementRenderers.renderLabeled(node, recursor());
    }

    @Override
    public @Nullable Doc visitYield(final YieldTree node, final Void p) {
        return SimpleStatementRenderers.renderYield(node, recursor());
    }

    @Override
    public @Nullable Doc visitIdentifier(final IdentifierTree node, final Void p) {
        return new Doc.Text(node.getName().toString());
    }

    @Override
    public @Nullable Doc visitPrimitiveType(final PrimitiveTypeTree node, final Void p) {
        return new Doc.Text(node.getPrimitiveTypeKind().name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public @Nullable Doc visitLiteral(final LiteralTree node, final Void p) {
        final var text = unit.sourceOf(node);
        if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return new Doc.Text(text);
        }
        return new Doc.Concat(Doc.intersperse(new Doc.HardLine(), Arrays.stream(text.split("\n", -1))
            .map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
            .<Doc>map(Doc.Text::new)));
    }

    @Override
    public @Nullable Doc visitEmptyStatement(final EmptyStatementTree node, final Void p) {
        return new Doc.Text(";");
    }

    @Override
    public @Nullable Doc visitBreak(final BreakTree node, final Void p) {
        return node.getLabel() == null
            ? new Doc.Text("break;")
            : new Doc.Text("break " + node.getLabel() + ";");
    }

    @Override
    public @Nullable Doc visitContinue(final ContinueTree node, final Void p) {
        return node.getLabel() == null
            ? new Doc.Text("continue;")
            : new Doc.Text("continue " + node.getLabel() + ";");
    }

    @Override
    public @Nullable Doc visitConstantCaseLabel(final ConstantCaseLabelTree node, final Void p) {
        return scanOrText(node.getConstantExpression());
    }

    @Override
    public @Nullable Doc visitDefaultCaseLabel(final DefaultCaseLabelTree node, final Void p) {
        return new Doc.Text("default");
    }

    @Override
    public @Nullable Doc visitArrayType(final ArrayTypeTree node, final Void p) {
        return new Doc.Concat(List.of(scanOrText(node.getType()), new Doc.Text("[]")));
    }

    @Override
    public @Nullable Doc visitParameterizedType(final ParameterizedTypeTree node, final Void p) {
        final var args = node.getTypeArguments();
        if (args.isEmpty()) {
            return new Doc.Concat(List.of(scanOrText(node.getType()), new Doc.Text("<>")));
        }
        final var argDocs = Doc.intersperse(new Doc.Text(", "), args.stream().<Doc>map(this::scanOrText));
        return new Doc.Concat(Stream.concat(
            Stream.<Doc>of(scanOrText(node.getType()), new Doc.Text("<")),
            Stream.concat(argDocs, Stream.<Doc>of(new Doc.Text(">")))));
    }

    @Override
    public @Nullable Doc visitUnionType(final UnionTypeTree node, final Void p) {
        return new Doc.Concat(Doc.intersperse(new Doc.Text(" | "), node.getTypeAlternatives().stream()
            .<Doc>map(this::scanOrText)));
    }

    @Override
    public @Nullable Doc visitIntersectionType(final IntersectionTypeTree node, final Void p) {
        return new Doc.Concat(Doc.intersperse(new Doc.Text(" & "), node.getBounds().stream()
            .<Doc>map(this::scanOrText)));
    }

    @Override
    public @Nullable Doc visitWildcard(final WildcardTree node, final Void p) {
        return switch (node.getKind()) {
            case EXTENDS_WILDCARD -> new Doc.Concat(List.of(new Doc.Text("? extends "), scanOrText(node.getBound())));
            case SUPER_WILDCARD -> new Doc.Concat(List.of(new Doc.Text("? super "), scanOrText(node.getBound())));
            case UNBOUNDED_WILDCARD -> new Doc.Text("?");
            default -> throw new IllegalStateException("unexpected wildcard kind: " + node.getKind());
        };
    }

    @Override
    public @Nullable Doc visitTypeParameter(final TypeParameterTree node, final Void p) {
        final var nameDoc = (Doc) new Doc.Text(node.getName().toString());
        final var bounds = node.getBounds();
        if (bounds.isEmpty()) {
            return nameDoc;
        }
        final var boundDocs = Doc.intersperse(new Doc.Text(" & "), bounds.stream().<Doc>map(this::scanOrText));
        return new Doc.Concat(Stream.concat(
            Stream.<Doc>of(nameDoc, new Doc.Text(" extends ")),
            boundDocs));
    }

    @Override
    public @Nullable Doc visitAnnotatedType(final AnnotatedTypeTree node, final Void p) {
        return new Doc.Concat(Stream.concat(
            node.getAnnotations().stream().<Doc>flatMap(a -> Stream.<Doc>of(scanOrText(a), new Doc.Text(" "))),
            Stream.<Doc>of(scanOrText(node.getUnderlyingType()))));
    }

    @Override
    public @Nullable Doc visitAnnotation(final AnnotationTree node, final Void p) {
        final var prefix = new Doc.Concat(List.of(new Doc.Text("@"), scanOrText(node.getAnnotationType())));
        final var args = node.getArguments();
        if (args.isEmpty()) {
            return prefix;
        }
        final var argDocs = Doc.intersperse(new Doc.Text(", "), args.stream().<Doc>map(this::scanOrText));
        return new Doc.Concat(Stream.concat(
            Stream.concat(Stream.<Doc>of(prefix, new Doc.Text("(")), argDocs),
            Stream.<Doc>of(new Doc.Text(")"))));
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
            scanOrText(param.getType()),
            new Doc.Text(" " + param.getName() + ")")));
        final var stmts = BlockRenderer.blockStmts(node.getBlock(), recursor());
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

    @Override
    public @Nullable Doc visitMemberReference(final MemberReferenceTree node, final Void p) {
        final var qualifier = scanOrText(node.getQualifierExpression());
        final var name = node.getMode() == MemberReferenceTree.ReferenceMode.NEW
            ? "new"
            : node.getName().toString();
        final var typeArgs = node.getTypeArguments();
        if (typeArgs == null || typeArgs.isEmpty()) {
            return new Doc.Concat(List.of(qualifier, new Doc.Text("::" + name)));
        }
        final var argDocs = Doc.intersperse(new Doc.Text(", "), typeArgs.stream().<Doc>map(this::scanOrText));
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.<Doc>of(qualifier, new Doc.Text("::<")),
                argDocs),
            Stream.<Doc>of(new Doc.Text(">" + name))));
    }

    private DocBuilder(final ParsedUnit unit, final GrindConfig config) {
        this.unit = unit;
        this.config = config;
        this.attacher = new CommentAttacher(unit);
    }

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
