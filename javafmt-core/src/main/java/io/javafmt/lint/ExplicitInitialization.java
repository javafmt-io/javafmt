package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.Modifier;

/**
 * Removes explicit initialization to the type's default value for fields only (not locals).
 * Locals often use explicit init as documentation of intent, so they are left alone.
 *
 * <p>{@code final} fields and fields of an interface or annotation type keep their initializer:
 * those have no later opportunity to be assigned, so the rewrite would not compile.
 */
public final class ExplicitInitialization implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
                edits -> new TreePathScanner<>() {

                    @Override
                    public Void visitVariable(final VariableTree node, final Void p) {
                        if (getCurrentPath().getParentPath().getLeaf() instanceof ClassTree owner && isRewritableField(node, owner)) {
                            removeDefaultInit(node, unit).ifPresent(edits::add);
                        }
                        return super.visitVariable(node, null);
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

    private static Optional<LintEdit> removeDefaultInit(final VariableTree field, final ParsedUnit unit) {
        final var init = field.getInitializer();
        if (!(init instanceof LiteralTree lit)) {
            return Optional.empty();
        }
        if (!isDefaultForType(field.getType(), lit)) {
            return Optional.empty();
        }
        return buildDeleteEdit(field, lit, unit);
    }

    private static boolean isDefaultForType(final Tree type, final LiteralTree lit) {
        return switch (type.getKind()) {
            case PRIMITIVE_TYPE -> isDefaultPrimitive((PrimitiveTypeTree) type, lit);
            case IDENTIFIER, PARAMETERIZED_TYPE, ARRAY_TYPE -> lit.getKind() == Tree.Kind.NULL_LITERAL;
            default -> false;
        };
    }

    private static boolean isZeroInt(final LiteralTree lit) {
        return lit.getKind() == Tree.Kind.INT_LITERAL && Integer.valueOf(0).equals(lit.getValue());
    }

    private static boolean isDefaultPrimitive(final PrimitiveTypeTree type, final LiteralTree lit) {
        return switch (type.getPrimitiveTypeKind()) {
            case INT, BYTE, SHORT -> lit.getKind() == Tree.Kind.INT_LITERAL && Integer.valueOf(0).equals(lit.getValue());
            // A char's default is written either way, and '\0' is the more common of the two.
            case CHAR -> isZeroInt(lit) || lit.getKind() == Tree.Kind.CHAR_LITERAL && Character.valueOf('\0').equals(lit.getValue());
            case LONG -> lit.getKind() == Tree.Kind.LONG_LITERAL && Long.valueOf(0L).equals(lit.getValue());
            case FLOAT -> lit.getKind() == Tree.Kind.FLOAT_LITERAL && Float.valueOf(0.0F).equals(lit.getValue());
            case DOUBLE -> lit.getKind() == Tree.Kind.DOUBLE_LITERAL && Double.valueOf(0.0).equals(lit.getValue());
            case BOOLEAN -> lit.getKind() == Tree.Kind.BOOLEAN_LITERAL && Boolean.FALSE.equals(lit.getValue());
            default -> false;
        };
    }

    /**
     * Deletes {@code " = <default>"} from the field declaration. The {@code '='} is found by
     * scanning backward from the initializer, bounded by the declaration's own start so a
     * malformed span can never latch onto an unrelated {@code '='} earlier in the file, and
     * skipping any {@code '='} that sits inside a comment — {@code int x = /* default = zero *&#47; 0;}
     * used to stop at the one in the comment and delete from there, leaving an unterminated comment
     * that came back to the user as their own parse error.
     *
     * <p>No edit is produced when a comment falls inside the span being deleted: the author wrote
     * it and deleting the initializer would take it with it.
     */
    private static Optional<LintEdit> buildDeleteEdit(final VariableTree field, final LiteralTree init, final ParsedUnit unit) {
        final var declStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), field);
        final var initStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), init);
        final var initEnd = (int) unit.sourcePositions().getEndPosition(unit.tree(), init);
        if (declStart < 0 || initStart < 0 || initEnd < 0) {
            return Optional.empty();
        }
        final var src = unit.source();
        final var comments = CommentScanner.scan(src.substring(declStart, initEnd))
                .stream()
                .map(c -> new Span(declStart + c.start(), declStart + c.end()))
                .toList();
        var eqPos = initStart - 1;
        while (eqPos > declStart && (src.charAt(eqPos) != '=' || covers(comments, eqPos))) {
            eqPos--;
        }
        if (src.charAt(eqPos) != '=' || covers(comments, eqPos)) {
            return Optional.empty();
        }
        while (eqPos > declStart && src.charAt(eqPos - 1) == ' ') {
            eqPos--;
        }
        final var deleted = new Span(eqPos, initEnd);
        return comments.stream().anyMatch(deleted::overlaps) ? Optional.empty() : Optional.of(new LintEdit(eqPos, initEnd, ""));
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
