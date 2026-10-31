package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;

/**
 * Removes explicit initialization to the type's default value for fields only (not locals).
 * Locals often use explicit initialization as documentation of intent, so they are left alone.
 *
 * <p>{@code final} fields and fields of an interface or annotation type keep their initializer:
 * those have no later opportunity to be assigned, so the rewrite would not compile.
 */
public final class ExplicitInitialization implements LintRule {
    @Override
    public LintResult apply(final LintContext context) {
        final var unit = context.unit();
        return LintScan.edits(
                context,
                edits -> new ChainScanner<>() {
                    @Override
                    public Void visitClass(final ClassTree node, final Void p) {
                        node.getMembers()
                                .stream()
                                .flatMap(
                                        m -> m instanceof VariableTree field && isRewritableField(field, node)
                                                ? removeDefaultInitializer(field, unit).stream()
                                                : Stream.empty())
                                .forEach(edits::add);
                        return super.visitClass(node, null);
                    }
                });
    }

    /**
     * A {@code final} field has no second chance to be assigned a value, and every field of an
     * interface or annotation type is implicitly {@code static final}, so dropping the initializer
     * turns "redundant" into "might not have been initialized".
     *
     * <p>A class with a superclass is excluded as well, because there the initializer is not always
     * redundant. Instance field initializers run after the superclass constructor, so if that
     * constructor calls a method the subclass overrides and the override assigns the field, the
     * explicit default is a real write that happens afterwards and overwrites it. Dropping it
     * changes the value the program observes.
     */
    private static boolean isRewritableField(final VariableTree field, final ClassTree owner) {
        return !field.getModifiers().getFlags().contains(Modifier.FINAL)
            && owner.getKind() != Tree.Kind.INTERFACE
            && owner.getKind() != Tree.Kind.ANNOTATION_TYPE
            && owner.getExtendsClause() == null;
    }

    private static Optional<LintEdit> removeDefaultInitializer(final VariableTree field, final ParsedUnit unit) {
        final var initializer = field.getInitializer();
        if (!(initializer instanceof LiteralTree literal)) {
            return Optional.empty();
        }
        if (!isDefaultForType(field.getType(), literal)) {
            return Optional.empty();
        }
        return buildDeleteEdit(field, literal, unit);
    }

    private static boolean isDefaultForType(final Tree type, final LiteralTree literal) {
        return switch (type.getKind()) {
            case PRIMITIVE_TYPE -> isDefaultPrimitive((PrimitiveTypeTree) type, literal);
            case IDENTIFIER, PARAMETERIZED_TYPE, ARRAY_TYPE -> literal.getKind() == Tree.Kind.NULL_LITERAL;
            default -> false;
        };
    }

    private static boolean isZeroInt(final LiteralTree literal) {
        return literal.getKind() == Tree.Kind.INT_LITERAL && Integer.valueOf(0).equals(literal.getValue());
    }

    private static boolean isDefaultPrimitive(final PrimitiveTypeTree type, final LiteralTree literal) {
        return switch (type.getPrimitiveTypeKind()) {
            case INT, BYTE, SHORT -> literal.getKind() == Tree.Kind.INT_LITERAL && Integer.valueOf(0).equals(literal.getValue());
            // A char's default is written either way, and '\0' is the more common of the two.
            case CHAR -> isZeroInt(literal) || (literal.getKind() == Tree.Kind.CHAR_LITERAL && Character.valueOf('\0').equals(literal.getValue()));
            case LONG -> literal.getKind() == Tree.Kind.LONG_LITERAL && Long.valueOf(0L).equals(literal.getValue());
            case FLOAT -> literal.getKind() == Tree.Kind.FLOAT_LITERAL && Float.valueOf(0.0F).equals(literal.getValue());
            case DOUBLE -> literal.getKind() == Tree.Kind.DOUBLE_LITERAL && Double.valueOf(0.0).equals(literal.getValue());
            case BOOLEAN -> literal.getKind() == Tree.Kind.BOOLEAN_LITERAL && Boolean.FALSE.equals(literal.getValue());
            default -> false;
        };
    }

    /**
     * Deletes {@code " = <default>"} from the field declaration. The {@code '='} is found by
     * scanning backward from the initializer, bounded by the declaration's own start so a
     * malformed span can never latch onto an unrelated {@code '='} earlier in the file, and
     * skipping any {@code '='} that sits inside a block comment between the name and the initializer.
     *
     * <p>No edit is produced when a comment falls inside the span being deleted: the author wrote
     * it and deleting the initializer would take it with it.
     */
    private static Optional<LintEdit> buildDeleteEdit(final VariableTree field, final LiteralTree initializer, final ParsedUnit unit) {
        final var declarationStart = unit.startOf(field);
        final var initializerStart = unit.startOf(initializer);
        final var initializerEnd = unit.endOf(initializer);
        if (declarationStart < 0 || initializerStart < 0 || initializerEnd < 0) {
            return Optional.empty();
        }
        final var src = unit.source();
        final var comments = CommentScanner.scan(src.substring(declarationStart, initializerEnd))
                .stream()
                .map(c -> new Span(declarationStart + c.start(), declarationStart + c.end()))
                .toList();
        var eqPos = initializerStart - 1;
        while (eqPos > declarationStart && (src.charAt(eqPos) != '=' || covers(comments, eqPos))) {
            eqPos--;
        }
        if (src.charAt(eqPos) != '=' || covers(comments, eqPos)) {
            return Optional.empty();
        }
        while (eqPos > declarationStart && src.charAt(eqPos - 1) == ' ') {
            eqPos--;
        }
        final var deleted = new Span(eqPos, initializerEnd);
        return comments.stream().anyMatch(deleted::overlaps) ? Optional.empty() : Optional.of(new LintEdit(eqPos, initializerEnd, ""));
    }

    private static boolean covers(final List<Span> spans, final int pos) {
        return spans.stream().anyMatch(span -> span.covers(pos));
    }

    private record Span(int start, int end) {
        boolean covers(final int pos) {
            return start <= pos && pos < end;
        }

        boolean overlaps(final Span other) {
            return start < other.end() && other.start() < end;
        }
    }
}
