package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Removes explicit initialization to the type's default value for fields only (not locals).
 * Locals often use explicit init as documentation of intent, so they are left alone.
 */
public final class ExplicitInitialization implements LintRule {

    @Override
    public String name() {
        return "ExplicitInitialization";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var edits = new ArrayList<LintEdit>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitVariable(final VariableTree node, final Void p) {
                if (getCurrentPath().getParentPath().getLeaf() instanceof ClassTree) {
                    removeDefaultInit(node, unit).ifPresent(edits::add);
                }
                return super.visitVariable(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return LintResult.ofEdits(edits);
    }

    private static Optional<LintEdit> removeDefaultInit(
            final VariableTree field, final ParsedUnit unit) {
        final var init = field.getInitializer();
        if (!(init instanceof LiteralTree lit)) {
            return Optional.empty();
        }
        if (!isDefaultForType(field.getType(), lit)) {
            return Optional.empty();
        }
        return buildDeleteEdit(lit, unit);
    }

    private static boolean isDefaultForType(final Tree type, final LiteralTree lit) {
        return switch (type.getKind()) {
            case PRIMITIVE_TYPE -> isDefaultPrimitive((PrimitiveTypeTree) type, lit);
            case IDENTIFIER, PARAMETERIZED_TYPE, ARRAY_TYPE -> lit.getKind() == Tree.Kind.NULL_LITERAL;
            default -> false;
        };
    }

    private static boolean isDefaultPrimitive(final PrimitiveTypeTree type, final LiteralTree lit) {
        return switch (type.getPrimitiveTypeKind()) {
            case INT, BYTE, SHORT, CHAR ->
                lit.getKind() == Tree.Kind.INT_LITERAL && Integer.valueOf(0).equals(lit.getValue());
            case LONG ->
                lit.getKind() == Tree.Kind.LONG_LITERAL && Long.valueOf(0L).equals(lit.getValue());
            case FLOAT ->
                lit.getKind() == Tree.Kind.FLOAT_LITERAL && Float.valueOf(0f).equals(lit.getValue());
            case DOUBLE ->
                lit.getKind() == Tree.Kind.DOUBLE_LITERAL && Double.valueOf(0d).equals(lit.getValue());
            case BOOLEAN ->
                lit.getKind() == Tree.Kind.BOOLEAN_LITERAL && Boolean.FALSE.equals(lit.getValue());
            default -> false;
        };
    }

    private static Optional<LintEdit> buildDeleteEdit(final LiteralTree init, final ParsedUnit unit) {
        final var initStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), init);
        final var initEnd = (int) unit.sourcePositions().getEndPosition(unit.tree(), init);
        if (initStart < 0 || initEnd < 0) {
            return Optional.empty();
        }
        final var src = unit.source();
        // Scan backwards from initStart to find '='
        var eqPos = initStart - 1;
        while (eqPos > 0 && src.charAt(eqPos) != '=') {
            eqPos--;
        }
        if (eqPos <= 0 || src.charAt(eqPos) != '=') {
            return Optional.empty();
        }
        // Include whitespace before '='
        while (eqPos > 0 && src.charAt(eqPos - 1) == ' ') {
            eqPos--;
        }
        return Optional.of(new LintEdit(eqPos, initEnd, ""));
    }
}
