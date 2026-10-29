package io.javafmt.builder;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import io.javafmt.doc.Doc;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

@UtilityClass
final class ExpressionRenderers {

    /**
     * A non-logical binary expression breaks before the operator when the line does not fit.
     * Unlike a logical chain this does not collect
     * same-precedence operands: each binary node carries its own group, so an overlong
     * expression breaks at its outermost operator first and only breaks further in if that is
     * still not enough. Width is the sole trigger, which keeps the choice stable across a
     * re-format.
     */
    Doc renderBinary(final BinaryTree node, final Recursor recursor) {
        if (isLogical(node)) {
            return renderLogicalChain(node, recursor);
        }
        final var spine = Stream.<@Nullable BinaryTree>iterate(
                node,
                Objects::nonNull,
                b -> b.getLeftOperand() instanceof BinaryTree left && !isLogical(left) ? left : null)
                .map(Objects::requireNonNull)
                .toList();
        var doc = recursor.scan(spine.getLast().getLeftOperand());
        for (final var operator : spine.reversed()) {
            final var group = operatorGroup(doc, operator, recursor);
            doc = operator == node ? group : recursor.attach(operator, group);
        }
        return doc;
    }

    private Doc operatorGroup(final Doc left, final BinaryTree operator, final Recursor recursor) {
        return new Doc.Group(
                Doc.concat(
                        left,
                        Doc.continuationIndent(
                                Doc.concat(Doc.LINE, new Doc.Text(binaryOperatorText(operator) + " "), recursor.scan(operator.getRightOperand())))));
    }

    private boolean isLogical(final BinaryTree node) {
        return node.getKind() == Tree.Kind.CONDITIONAL_AND || node.getKind() == Tree.Kind.CONDITIONAL_OR;
    }

    /**
     * Operands always render in compact mode: nested chains inside a logical operand break
     * only on the line width, not on author-broke layout. This keeps the broken logical chain
     * visually flat per operand
     * and — crucially — keeps the format idempotent (whether the printer breaks the logical
     * group is a line-width decision that may flip between round 1 and round 2, so the
     * operands' shape must not depend on it).
     */
    private Doc renderLogicalChain(final BinaryTree node, final Recursor recursor) {
        final var operands = collectLogicalOperands(node, node.getKind()).toList();
        final var operator = binaryOperatorText(node);
        final var tail = operands.stream()
                .skip(1)
                .<Doc>map(op -> new Doc.Indent(Doc.concat(Doc.LINE, new Doc.Text(operator + " "), recursor.scanCompact(op))));
        final var contents = new Doc.Concat(Stream.concat(Stream.of(recursor.scanCompact(operands.getFirst())), tail));
        final var authorBroke = !recursor.inCompactContext() && operands.stream().limit(operands.size() - 1).anyMatch(recursor::isFollowedByNewline);
        return MethodChainRenderer.chainGroup(contents, authorBroke);
    }

    private Stream<ExpressionTree> collectLogicalOperands(final ExpressionTree root, final Tree.Kind kind) {
        final var pending = new ArrayDeque<ExpressionTree>(List.of(root));
        final var operands = Stream.<ExpressionTree>builder();
        while (!pending.isEmpty()) {
            final var expr = pending.pop();
            if (expr instanceof BinaryTree bin && bin.getKind() == kind) {
                pending.push(bin.getRightOperand());
                pending.push(bin.getLeftOperand());
            } else {
                operands.add(expr);
            }
        }
        return operands.build();
    }

    Doc renderUnary(final UnaryTree node, final Recursor recursor) {
        final var op = unaryOperatorText(node.getKind());
        if (isPostfix(node.getKind())) {
            return Doc.concat(recursor.scan(node.getExpression()), new Doc.Text(op));
        }
        final var prefix = needsSpaceBeforeOperand(op, node.getExpression()) ? op + " " : op;
        return Doc.concat(new Doc.Text(prefix), recursor.scan(node.getExpression()));
    }

    private boolean isPostfix(final Tree.Kind kind) {
        return kind == Tree.Kind.POSTFIX_INCREMENT || kind == Tree.Kind.POSTFIX_DECREMENT;
    }

    /**
     * Whether a prefix {@code -} or {@code +} needs a space before its operand. Glued together,
     * {@code - -x} prints as {@code --x} and javac reads the two characters as one decrement
     * operator: the negation of a negation becomes a decrement of {@code x}, which either fails to
     * compile or, on a mutable local, compiles as a different program. Only the sign operators can
     * collide this way — {@code !!b} and {@code ~~x} are not operators.
     */
    private boolean needsSpaceBeforeOperand(final String op, final ExpressionTree operand) {
        return (op.equals("-") || op.equals("+"))
            && operand instanceof UnaryTree inner
            && !isPostfix(inner.getKind())
            && unaryOperatorText(inner.getKind()).startsWith(op);
    }

    Doc renderAssignment(final AssignmentTree node, final Recursor recursor) {
        return renderAssignmentLike(recursor.scan(node.getVariable()), "=", node.getExpression(), recursor);
    }

    Doc renderCompoundAssignment(final CompoundAssignmentTree node, final Recursor recursor) {
        return renderAssignmentLike(recursor.scan(node.getVariable()), compoundOperatorText(node.getKind()), node.getExpression(), recursor);
    }

    /**
     * Breaks after the operator, leaving the right-hand side on its own continuation line — but only
     * when the value has no break point of its own. A wrappable value (a call, an operator chain, a
     * ternary) already breaks where it should, and breaking after the operator as well would just
     * push the whole value down a line and in one indent level further.
     */
    private Doc renderAssignmentLike(final Doc target, final String operator, final ExpressionTree value, final Recursor recursor) {
        final var valueDoc = recursor.scan(value);
        if (Doc.canBreakInternally(valueDoc)) {
            return Doc.concat(target, new Doc.Text(" " + operator + " "), valueDoc);
        }
        return new Doc.Group(Doc.concat(target, new Doc.Text(" " + operator), Doc.continuationIndent(Doc.concat(Doc.LINE, valueDoc))));
    }

    /** Breaks before both {@code ?} and {@code :} together, so the two arms line up. */
    Doc renderConditionalExpression(final ConditionalExpressionTree node, final Recursor recursor) {
        return new Doc.Group(
                Doc.concat(
                        recursor.scan(node.getCondition()),
                        Doc.continuationIndent(
                                Doc.concat(
                                        Doc.LINE,
                                        new Doc.Text("? "),
                                        recursor.scan(node.getTrueExpression()),
                                        Doc.LINE,
                                        new Doc.Text(": "),
                                        recursor.scan(node.getFalseExpression())))));
    }

    Doc renderNewArray(final NewArrayTree node, final Recursor recursor) {
        final var type = node.getType();
        final var dimensions = node.getDimensions();
        final var initializers = node.getInitializers();
        final var element = Optional.ofNullable(type).map(ExpressionRenderers::elementTypeOf);
        final var prefix = element.stream().flatMap(e -> Stream.of(new Doc.Text("new "), recursor.scan(e.type())));
        final var dims = dimensions.stream().flatMap(d -> Stream.of(new Doc.Text("["), recursor.scan(d), new Doc.Text("]")));
        final var sizelessBrackets = Stream.<Doc>generate(() -> new Doc.Text("[]")).limit(element.map(ElementType::sizelessLevels).orElse(0));
        final var implicitBrackets = hasImplicitArrayBrackets(type, dimensions, initializers)
                ? Stream.<Doc>of(new Doc.Text("[]"))
                : Stream.<Doc>empty();
        if (initializers == null) {
            return Doc.concatOf(Stream.of(prefix, dims, sizelessBrackets, implicitBrackets));
        }
        final var brace = type == null ? "{" : " {";
        if (initializers.isEmpty()) {
            return Doc.concatOf(Stream.of(prefix, dims, sizelessBrackets, implicitBrackets, Stream.of(new Doc.Text(brace + "}"))));
        }
        final var elements = WrappableList.blockOf(brace, ",", "}", withTrailingComma(initializers.stream().map(recursor::scan).toList()));
        return Doc.concatOf(Stream.of(prefix, dims, sizelessBrackets, implicitBrackets, Stream.of(elements)));
    }

    /**
     * The type an array creation allocates, split from the bracket levels javac folds into
     * {@link NewArrayTree#getType()}. In {@code new T[n][]} javac reports the type as {@code T[]}
     * and one dimension, {@code n}. Rendering that type as-is and then appending the dimensions
     * writes {@code new T[][n]}, which does not compile: the sized brackets come first and the
     * sizeless ones follow, so they have to be emitted separately.
     *
     * <p>Only plain {@link ArrayTypeTree} levels are split off. A type annotation binds to one
     * specific bracket level and putting it back would take the source text, so an annotated array
     * type is left whole and rendered as javac hands it over.
     */
    private ElementType elementTypeOf(final Tree type) {
        Tree current = type;
        var levels = 0;
        while (current instanceof ArrayTypeTree array) {
            current = array.getType();
            levels++;
        }
        return new ElementType(current, levels);
    }

    /**
     * The elements, with the trailing comma attached to the last one rather than to the closing
     * brace — written as part of the brace it would break onto the closing line as {@code ,}}.
     *
     * <p>The comma itself is {@code ArrayTrailingComma}'s: that rule writes one into the source of
     * every non-empty initializer, so this is reproducing a token rather than inventing one.
     */
    private Stream<Doc> withTrailingComma(final List<Doc> elements) {
        return IntStream.range(0, elements.size())
                .mapToObj(i -> i < elements.size() - 1 ? elements.get(i) : Doc.concat(elements.get(i), new Doc.Text(",")));
    }

    /**
     * Whether {@code node} is the {@code new T[]{...}} initializer-only array form. javac shifts
     * one bracket level into {@link NewArrayTree#getType()} for this form and leaves
     * {@link NewArrayTree#getDimensions()} empty, so the renderer must re-emit the missing
     * {@code []} between the type and the initializer.
     */
    private boolean hasImplicitArrayBrackets(
            @Nullable final Tree type,
            final List<? extends ExpressionTree> dimensions,
            @Nullable final List<? extends ExpressionTree> initializers) {
        return type != null && dimensions.isEmpty() && initializers != null;
    }

    Doc renderNewClass(final NewClassTree node, final Recursor recursor) {
        final var args = node.getArguments();
        final var argsDoc = args.isEmpty() ? (Doc) new Doc.Text("()") : WrappableList.of("(", ",", ")", args.stream().map(recursor::scan));
        final var body = node.getClassBody();
        final var headParts = Doc.concatOf(
                Stream.of(
                        qualifier(node, recursor),
                        Stream.of(new Doc.Text("new ")),
                        constructorTypeWitness(node, recursor),
                        Stream.of(recursor.scan(node.getIdentifier()), argsDoc)));
        if (body == null) {
            return headParts;
        }
        return Doc.concat(headParts, ClassBodyRenderer.render(body, recursor));
    }

    /**
     * The {@code outer.} of {@code outer.new Inner()}. An inner class instantiated against an
     * explicit enclosing instance carries it here rather than in the identifier, so dropping it
     * turns the expression into {@code new Inner()} — a different, usually uncompilable, program.
     */
    private Stream<Doc> qualifier(final NewClassTree node, final Recursor recursor) {
        return Optional.ofNullable(node.getEnclosingExpression())
                .stream()
                .flatMap(enclosing -> Stream.of(recursor.scan(enclosing), new Doc.Text(".")));
    }

    /** The {@code <T>} of {@code new <T>Foo()}, which binds the constructor's own type parameters. */
    private Stream<Doc> constructorTypeWitness(final NewClassTree node, final Recursor recursor) {
        final var typeArguments = node.getTypeArguments();
        if (typeArguments.isEmpty()) {
            return Stream.empty();
        }
        return Stream.of(
                Doc.concatOf(
                        Stream.of(
                                Stream.of(new Doc.Text("<")),
                                Doc.intersperse(new Doc.Text(", "), typeArguments.stream().map(recursor::scan)),
                                Stream.of(new Doc.Text(">")))));
    }

    Doc renderTypeCast(final TypeCastTree node, final Recursor recursor) {
        return Doc.concat(new Doc.Text("("), recursor.scan(node.getType()), new Doc.Text(") "), recursor.scan(node.getExpression()));
    }

    Doc renderParenthesized(final ParenthesizedTree node, final Recursor recursor) {
        return Doc.concat(new Doc.Text("("), recursor.scan(node.getExpression()), new Doc.Text(")"));
    }

    Doc renderInstanceOf(final InstanceOfTree node, final Recursor recursor) {
        final var pattern = node.getPattern();
        final var rhs = pattern != null ? recursor.scan(pattern) : recursor.scan(node.getType());
        return Doc.concat(recursor.scan(node.getExpression()), new Doc.Text(" instanceof "), rhs);
    }

    Doc renderBindingPattern(final BindingPatternTree node, final Recursor recursor) {
        final var variable = node.getVariable();
        final var typeDoc = variable.getType() == null ? new Doc.Text("var") : recursor.scan(variable.getType());
        // `if (o instanceof final String s)` is legal and the modifier was simply never read, so
        // the file came back refused rather than formatted.
        final var modifiers = ModifierRenderer.prefixOf(variable.getModifiers(), recursor);
        return Doc.concat(new Doc.Text(modifiers), typeDoc, new Doc.Text(" " + Names.of(variable)));
    }

    Doc renderDeconstructionPattern(final DeconstructionPatternTree node, final Recursor recursor) {
        final var nested = WrappableList.of("(", ",", ")", node.getNestedPatterns().stream().map(recursor::scan));
        return Doc.concat(recursor.scan(node.getDeconstructor()), nested);
    }

    Doc renderPatternCaseLabel(final PatternCaseLabelTree node, final Recursor recursor) {
        return recursor.scan(node.getPattern());
    }

    Doc renderConstantCaseLabel(final ConstantCaseLabelTree node, final Recursor recursor) {
        return recursor.scan(node.getConstantExpression());
    }

    Doc renderMemberSelect(final MemberSelectTree node, final Recursor recursor) {
        final var spine = Stream.<@Nullable MemberSelectTree>iterate(
                node,
                Objects::nonNull,
                s -> s.getExpression() instanceof MemberSelectTree inner ? inner : null)
                .map(Objects::requireNonNull)
                .toList();
        return spine.reversed()
                .stream()
                .reduce(
                        recursor.scan(spine.getLast().getExpression()),
                        (receiver, select) -> {
                            final var selected = Doc.concat(receiver, new Doc.Text("." + select.getIdentifier()));
                            return select == node ? selected : recursor.attach(select, selected);
                        },
                        (left, right) -> right);
    }

    Doc renderArrayAccess(final ArrayAccessTree node, final Recursor recursor) {
        return Doc.concat(recursor.scan(node.getExpression()), new Doc.Text("["), recursor.scan(node.getIndex()), new Doc.Text("]"));
    }

    Doc renderMemberReference(final MemberReferenceTree node, final Recursor recursor) {
        final var qualifier = recursor.scan(node.getQualifierExpression());
        final var name = node.getMode() == MemberReferenceTree.ReferenceMode.NEW ? "new" : node.getName().toString();
        final var typeArgs = node.getTypeArguments();
        if (typeArgs == null || typeArgs.isEmpty()) {
            return Doc.concat(qualifier, new Doc.Text("::" + name));
        }
        final var argDocs = Doc.intersperse(new Doc.Text(", "), typeArgs.stream().map(recursor::scan));
        return new Doc.Concat(
                Stream.concat(Stream.concat(Stream.of(qualifier, new Doc.Text("::<")), argDocs), Stream.<Doc>of(new Doc.Text(">" + name))));
    }

    Doc renderAnnotation(final AnnotationTree node, final Recursor recursor) {
        final var prefix = Doc.concat(new Doc.Text("@"), recursor.scan(node.getAnnotationType()));
        final var args = node.getArguments();
        if (args.isEmpty()) {
            return prefix;
        }
        return Doc.concat(prefix, WrappableList.of("(", ",", ")", args.stream().map(recursor::scan)));
    }

    private String binaryOperatorText(final BinaryTree node) {
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

    private String unaryOperatorText(final Tree.Kind kind) {
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

    private String compoundOperatorText(final Tree.Kind kind) {
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

    private record ElementType(Tree type, int sizelessLevels) {}
}
