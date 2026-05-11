package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;

/**
 * Replaces explicit local variable types with {@code var} when the initializer is a
 * {@code new} expression and the declared type exactly matches the instantiated type
 * (no widening). Diamond-inferred types and widened interface types are excluded.
 *
 * <p>Scoped to v1: only {@code new} expressions are checked. Method-call initializers
 * require type resolution to verify the inferred type matches the declared type.
 */
public final class LocalVarUseVar implements LintRule {

    @Override
    public String name() {
        return "LocalVarUseVar";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var edits = new ArrayList<LintEdit>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitVariable(final VariableTree node, final Void p) {
                if (isLocalBlock(getCurrentPath())
                        && !isVarType(node, unit)
                        && !(node.getType() instanceof PrimitiveTypeTree)
                        && node.getInitializer() instanceof NewClassTree newClass
                        && typesMatch(node, newClass, unit)) {
                    final var typeStart = (int) unit.sourcePositions()
                        .getStartPosition(unit.tree(), node.getType());
                    final var typeEnd = (int) unit.sourcePositions()
                        .getEndPosition(unit.tree(), node.getType());
                    if (typeStart >= 0 && typeEnd > typeStart) {
                        edits.add(new LintEdit(typeStart, typeEnd, "var"));
                    }
                }
                return super.visitVariable(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return LintResult.ofEdits(edits);
    }

    private static boolean isLocalBlock(final TreePath path) {
        final var parent = path.getParentPath();
        return parent != null && parent.getLeaf() instanceof BlockTree;
    }

    private static boolean isVarType(final VariableTree node, final ParsedUnit unit) {
        // Check the raw source text at the type position — more reliable than AST comparison
        // because javac may resolve `var` to its inferred type during attribution.
        final var typeStart = (int) unit.sourcePositions()
            .getStartPosition(unit.tree(), node.getType());
        if (typeStart < 0 || typeStart + 3 > unit.source().length()) {
            return false;
        }
        final var src = unit.source();
        return src.startsWith("var", typeStart)
            && (typeStart + 3 >= src.length()
                || !Character.isJavaIdentifierPart(src.charAt(typeStart + 3)));
    }

    private static boolean typesMatch(
            final VariableTree node, final NewClassTree newClass, final ParsedUnit unit) {
        final var identifier = newClass.getIdentifier();
        if (identifier == null) {
            return false;
        }
        final var typeStart = (int) unit.sourcePositions()
            .getStartPosition(unit.tree(), node.getType());
        final var identStart = (int) unit.sourcePositions()
            .getStartPosition(unit.tree(), identifier);
        if (typeStart < 0 || identStart < 0) {
            return false;
        }
        final var declaredSrc = unit.sourceOf(node.getType()).trim();
        final var instantiatedSrc = unit.sourceOf(identifier).trim();
        return declaredSrc.equals(instantiatedSrc);
    }
}
