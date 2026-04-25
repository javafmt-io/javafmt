package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.GrindConfig;
import io.github.jschneidereit.grind.parser.ParsedUnit;

import java.util.Set;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

public final class DocBuilder extends TreeScanner<@Nullable Doc, Void> {

    private static final Set<Tree.Kind> EXPECTED_FALLBACK_KINDS = Set.of(
        Tree.Kind.IDENTIFIER, Tree.Kind.PRIMITIVE_TYPE,
        Tree.Kind.BOOLEAN_LITERAL, Tree.Kind.CHAR_LITERAL,
        Tree.Kind.DOUBLE_LITERAL, Tree.Kind.FLOAT_LITERAL,
        Tree.Kind.INT_LITERAL, Tree.Kind.LONG_LITERAL,
        Tree.Kind.NULL_LITERAL, Tree.Kind.STRING_LITERAL,
        Tree.Kind.MODIFIERS, Tree.Kind.EMPTY_STATEMENT,
        Tree.Kind.BREAK, Tree.Kind.CONTINUE,
        Tree.Kind.TYPE_PARAMETER,
        Tree.Kind.CONSTANT_CASE_LABEL,
        Tree.Kind.UNION_TYPE,
        Tree.Kind.PARAMETERIZED_TYPE,
        Tree.Kind.ARRAY_TYPE);

    private final GrindConfig config;
    private final ParsedUnit unit;
    private final LeadingCommentAttacher attacher;
    private final java.util.List<Tree> fallbacks = new java.util.ArrayList<>();
    private final java.util.List<Diagnostic> diagnostics = new java.util.ArrayList<>();

    private Recursor recursor() {
        return new Recursor() {
            @Override
            public @Nullable Doc scan(final Tree node) {
                return DocBuilder.this.scan(node, null);
            }

            @Override
            public void emitWarning(final String message, final Tree at) {
                diagnostics.add(new Diagnostic.Warning(message, unit.positionOf(at)));
            }
        };
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
        return buildWithFallbacks(unit, config).doc();
    }

    public static BuildResult buildWithFallbacks(final ParsedUnit unit, final GrindConfig config) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(config, "config");
        final var builder = new DocBuilder(unit, config);
        final var doc = builder.visitCompilationUnit(unit.tree(), null);
        return new BuildResult(
            Objects.requireNonNull(doc, "visitCompilationUnit returned null"),
            List.copyOf(builder.fallbacks),
            List.copyOf(builder.diagnostics));
    }

    public record BuildResult(Doc doc, List<Tree> fallbacks, List<Diagnostic> diagnostics) {}

    @Override
    public @Nullable Doc scan(final @Nullable Tree tree, final Void p) {
        if (tree == null) {
            return null;
        }
        final var result = isHandled(tree) ? super.scan(tree, null) : null;
        final Doc rendered;
        if (result != null) {
            rendered = result;
        } else {
            fallbacks.add(tree);
            if (!EXPECTED_FALLBACK_KINDS.contains(tree.getKind())) {
                diagnostics.add(new Diagnostic.Warning(
                    "fell back to javac pretty-printer for " + tree.getKind(),
                    unit.positionOf(tree)));
            }
            rendered = textFallback(tree);
        }
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
            || tree instanceof AssertTree
            || tree instanceof SynchronizedTree
            || tree instanceof LabeledStatementTree
            || tree instanceof YieldTree
            || tree instanceof NewArrayTree
            || tree instanceof TypeCastTree
            || tree instanceof ParenthesizedTree
            || tree instanceof InstanceOfTree
            || tree instanceof BindingPatternTree
            || tree instanceof DeconstructionPatternTree
            || tree instanceof PatternCaseLabelTree
            || tree instanceof BinaryTree
            || tree instanceof UnaryTree
            || tree instanceof ConditionalExpressionTree
            || tree instanceof NewClassTree
            || tree instanceof AssignmentTree
            || tree instanceof CompoundAssignmentTree
            || tree instanceof MemberSelectTree
            || tree instanceof ArrayAccessTree;
    }

    private static Doc textFallback(final Tree tree) {
        final var s = tree.toString().stripTrailing();
        if (s.indexOf('\n') < 0 && s.indexOf('\r') < 0) {
            return new Doc.Text(s);
        }
        return new Doc.Concat(Doc.intersperse(new Doc.HardLine(), java.util.Arrays.stream(s.split("\n", -1))
            .map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
            .<Doc>map(Doc.Text::new)));
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
        return recursor().scanOrText(tree);
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

            @Override
            public java.util.List<io.github.jschneidereit.grind.parser.CommentToken> tail(final Tree node) {
                return unit.tailOf(node);
            }
        };
    }

    private static ParsedUnit emptyUnit(final CompilationUnitTree tree) {
        return new ParsedUnit(
            tree,
            NO_POSITIONS,
            List.of(),
            List.of(),
            new java.util.IdentityHashMap<>(),
            new java.util.IdentityHashMap<>(),
            new java.util.IdentityHashMap<>(),
            new java.util.IdentityHashMap<>());
    }

    private static final com.sun.source.util.SourcePositions NO_POSITIONS = new com.sun.source.util.SourcePositions() {
        @Override
        public long getStartPosition(final CompilationUnitTree file, final Tree tree) {
            return javax.tools.Diagnostic.NOPOS;
        }

        @Override
        public long getEndPosition(final CompilationUnitTree file, final Tree tree) {
            return javax.tools.Diagnostic.NOPOS;
        }
    };
}
