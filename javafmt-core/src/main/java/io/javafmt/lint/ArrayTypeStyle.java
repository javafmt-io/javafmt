package io.javafmt.lint;

import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Enforces Java-style array notation: {@code String[] args} instead of C-style
 * {@code String args[]}. Detects C-style by checking whether brackets appear after the
 * variable name in the source text (C-style) vs. after the element type (Java-style).
 */
public final class ArrayTypeStyle implements LintRule {

    @Override
    public String name() {
        return "ArrayTypeStyle";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var edits = new ArrayList<LintEdit>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitVariable(final VariableTree node, final Void p) {
                fixFor(node, unit).ifPresent(edits::add);
                return super.visitVariable(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return LintResult.ofEdits(edits);
    }

    private static Optional<LintEdit> fixFor(final VariableTree node, final ParsedUnit unit) {
        if (!(node.getType() instanceof ArrayTypeTree)) {
            return Optional.empty();
        }
        final var positions = unit.sourcePositions();
        final var root = unit.tree();
        final var typeStart = (int) positions.getStartPosition(root, node.getType());
        final var typeEnd = (int) positions.getEndPosition(root, node.getType());
        if (typeStart < 0 || typeEnd < 0 || typeEnd > unit.source().length()) {
            return Optional.empty();
        }
        // In javac, C-style ArrayTypeTree end positions span "Type name[]", whereas Java-style
        // spans only "Type[]". Detect C-style by checking if the variable name appears inside
        // the type's source span.
        final var typeSrc = unit.source().substring(typeStart, typeEnd);
        final var name = node.getName().toString();
        final var nameIdx = typeSrc.lastIndexOf(name);
        if (nameIdx < 0) {
            return Optional.empty(); // Java-style, name is not in type span
        }
        // Guard against the name being a substring of the type name itself
        if (nameIdx > 0 && Character.isJavaIdentifierPart(typeSrc.charAt(nameIdx - 1))) {
            return Optional.empty();
        }
        final var afterNameInType = typeSrc.substring(nameIdx + name.length());
        final var brackets = extractBracketSuffix(afterNameInType);
        if (brackets.isEmpty()) {
            return Optional.empty();
        }
        // Everything before the name (stripped trailing space) is the Java-style type
        final var typeBeforeName = typeSrc.substring(0, nameIdx).stripTrailing();
        final var replacement = typeBeforeName + brackets + " " + name;
        return Optional.of(new LintEdit(typeStart, typeEnd, replacement));
    }

    private static String extractBracketSuffix(final String afterName) {
        final var sb = new StringBuilder();
        for (var i = 0; i < afterName.length(); i++) {
            final var ch = afterName.charAt(i);
            if (ch == '[' || ch == ']') {
                sb.append(ch);
            } else if (!Character.isWhitespace(ch)) {
                break;
            }
        }
        return sb.toString();
    }
}
