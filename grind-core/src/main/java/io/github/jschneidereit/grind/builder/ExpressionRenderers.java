package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;

import io.github.jschneidereit.grind.doc.Doc;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class ExpressionRenderers {

    static Doc renderBinary(final BinaryTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            recursor.scan(node.getLeftOperand()),
            new Doc.Text(" " + binaryOperatorText(node) + " "),
            recursor.scan(node.getRightOperand())));
    }

    static Doc renderUnary(final UnaryTree node, final Recursor recursor) {
        final var op = unaryOperatorText(node.getKind());
        final var postfix = node.getKind() == Tree.Kind.POSTFIX_INCREMENT || node.getKind() == Tree.Kind.POSTFIX_DECREMENT;
        return postfix
            ? new Doc.Concat(List.of(recursor.scan(node.getExpression()), new Doc.Text(op)))
            : new Doc.Concat(List.of(new Doc.Text(op), recursor.scan(node.getExpression())));
    }

    static Doc renderAssignment(final AssignmentTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            recursor.scan(node.getVariable()),
            new Doc.Text(" = "),
            recursor.scan(node.getExpression())));
    }

    static Doc renderCompoundAssignment(final CompoundAssignmentTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            recursor.scan(node.getVariable()),
            new Doc.Text(" " + compoundOperatorText(node.getKind()) + " "),
            recursor.scan(node.getExpression())));
    }

    static Doc renderConditionalExpression(final ConditionalExpressionTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            recursor.scan(node.getCondition()),
            new Doc.Text(" ? "),
            recursor.scan(node.getTrueExpression()),
            new Doc.Text(" : "),
            recursor.scan(node.getFalseExpression())));
    }

    static Doc renderNewArray(final NewArrayTree node, final Recursor recursor) {
        final var type = node.getType();
        final var dimensions = node.getDimensions();
        final var initializers = node.getInitializers();
        final var prefix = type == null
            ? Stream.<Doc>empty()
            : Stream.<Doc>of(new Doc.Text("new "), recursor.scan(type));
        final var dims = dimensions.stream()
            .<Doc>flatMap(d -> Stream.<Doc>of(new Doc.Text("["), recursor.scan(d), new Doc.Text("]")));
        // For `new T[]{...}` form, javac shifts one bracket level into getType() and leaves
        // dimensions empty, so we re-emit the missing `[]` between type and the initializer.
        final var implicitBrackets = (type != null && dimensions.isEmpty() && initializers != null)
            ? Stream.<Doc>of(new Doc.Text("[]"))
            : Stream.<Doc>empty();
        if (initializers == null) {
            return new Doc.Concat(Stream.concat(Stream.concat(prefix, dims), implicitBrackets));
        }
        final var brace = type == null ? "{" : " {";
        if (initializers.isEmpty()) {
            return new Doc.Concat(Stream.concat(
                Stream.concat(Stream.concat(prefix, dims), implicitBrackets),
                Stream.<Doc>of(new Doc.Text(brace + "}"))));
        }
        final var elements = Doc.intersperse(new Doc.Text(", "), initializers.stream()
            .<Doc>map(recursor::scan));
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.concat(Stream.concat(prefix, dims), implicitBrackets),
                Stream.concat(Stream.<Doc>of(new Doc.Text(brace)), elements)),
            Stream.<Doc>of(new Doc.Text(",}"))));
    }

    static Doc renderNewClass(final NewClassTree node, final Recursor recursor) {
        final var args = node.getArguments();
        final var argsDoc = args.isEmpty()
            ? (Doc) new Doc.Text("()")
            : new Doc.Concat(Stream.concat(
                Stream.concat(
                    Stream.<Doc>of(new Doc.Text("(")),
                    Doc.intersperse(new Doc.Text(", "), args.stream().<Doc>map(recursor::scan))),
                Stream.<Doc>of(new Doc.Text(")"))));
        final var body = node.getClassBody();
        final var headParts = List.<Doc>of(new Doc.Text("new "), recursor.scan(node.getIdentifier()), argsDoc);
        if (body == null) {
            return new Doc.Concat(headParts);
        }
        final var members = body.getMembers().stream()
            .<Doc>flatMap(m -> Optional.<Doc>ofNullable(recursor.scanNullable(m)).stream())
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

    static Doc renderTypeCast(final TypeCastTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            new Doc.Text("("),
            recursor.scan(node.getType()),
            new Doc.Text(") "),
            recursor.scan(node.getExpression())));
    }

    static Doc renderParenthesized(final ParenthesizedTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            new Doc.Text("("),
            recursor.scan(node.getExpression()),
            new Doc.Text(")")));
    }

    static Doc renderInstanceOf(final InstanceOfTree node, final Recursor recursor) {
        final var pattern = node.getPattern();
        final var rhs = pattern != null ? recursor.scan(pattern) : recursor.scan(node.getType());
        return new Doc.Concat(List.of(
            recursor.scan(node.getExpression()),
            new Doc.Text(" instanceof "),
            rhs));
    }

    static Doc renderBindingPattern(final BindingPatternTree node, final Recursor recursor) {
        final var variable = node.getVariable();
        final var typeDoc = variable.getType() == null ? new Doc.Text("var") : recursor.scan(variable.getType());
        return new Doc.Concat(List.of(typeDoc, new Doc.Text(" " + variable.getName())));
    }

    static Doc renderDeconstructionPattern(final DeconstructionPatternTree node, final Recursor recursor) {
        final var nested = Doc.intersperse(new Doc.Text(", "), node.getNestedPatterns().stream()
            .<Doc>map(recursor::scan));
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.<Doc>of(recursor.scan(node.getDeconstructor()), new Doc.Text("(")),
                nested),
            Stream.<Doc>of(new Doc.Text(")"))));
    }

    static Doc renderPatternCaseLabel(final PatternCaseLabelTree node, final Recursor recursor) {
        return recursor.scan(node.getPattern());
    }

    static Doc renderConstantCaseLabel(final ConstantCaseLabelTree node, final Recursor recursor) {
        return recursor.scan(node.getConstantExpression());
    }

    static Doc renderMemberSelect(final MemberSelectTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            recursor.scan(node.getExpression()),
            new Doc.Text("." + node.getIdentifier())));
    }

    static Doc renderArrayAccess(final ArrayAccessTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(
            recursor.scan(node.getExpression()),
            new Doc.Text("["),
            recursor.scan(node.getIndex()),
            new Doc.Text("]")));
    }

    static Doc renderMemberReference(final MemberReferenceTree node, final Recursor recursor) {
        final var qualifier = recursor.scan(node.getQualifierExpression());
        final var name = node.getMode() == MemberReferenceTree.ReferenceMode.NEW
            ? "new"
            : node.getName().toString();
        final var typeArgs = node.getTypeArguments();
        if (typeArgs == null || typeArgs.isEmpty()) {
            return new Doc.Concat(List.of(qualifier, new Doc.Text("::" + name)));
        }
        final var argDocs = Doc.intersperse(new Doc.Text(", "), typeArgs.stream().<Doc>map(recursor::scan));
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.<Doc>of(qualifier, new Doc.Text("::<")),
                argDocs),
            Stream.<Doc>of(new Doc.Text(">" + name))));
    }

    static Doc renderAnnotation(final AnnotationTree node, final Recursor recursor) {
        final var prefix = new Doc.Concat(List.of(new Doc.Text("@"), recursor.scan(node.getAnnotationType())));
        final var args = node.getArguments();
        if (args.isEmpty()) {
            return prefix;
        }
        final var argDocs = Doc.intersperse(new Doc.Text(", "), args.stream().<Doc>map(recursor::scan));
        return new Doc.Concat(Stream.concat(
            Stream.concat(Stream.<Doc>of(prefix, new Doc.Text("(")), argDocs),
            Stream.<Doc>of(new Doc.Text(")"))));
    }

    private static String binaryOperatorText(final BinaryTree node) {
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

    private ExpressionRenderers() {}
}
