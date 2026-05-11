package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.Diagnostic;
import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;

/**
 * Warns if a class appears to be a utility class (all-static methods and fields, not
 * abstract/interface/enum/record) but has no explicit private no-arg constructor. Without
 * a private constructor the class can be accidentally instantiated.
 */
public final class HideUtilityClassConstructor implements LintRule {

    private static final String MESSAGE =
        "utility class should have a private no-arg constructor to prevent instantiation";

    @Override
    public String name() {
        return "HideUtilityClassConstructor";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var diagnostics = new ArrayList<Diagnostic>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitClass(final ClassTree node, final Void p) {
                if (isUtilityCandidate(node) && !hasPrivateNoArgConstructor(node)) {
                    diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(node)));
                }
                return super.visitClass(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return new LintResult(List.of(), diagnostics);
    }

    private static boolean isUtilityCandidate(final ClassTree node) {
        if (node.getKind() != Tree.Kind.CLASS) {
            return false;
        }
        if (node.getModifiers().getFlags().contains(Modifier.ABSTRACT)) {
            return false;
        }
        final var members = node.getMembers();
        if (members.isEmpty()) {
            return false;
        }
        // All non-constructor members must be static; at least one method or field must exist
        var hasStaticContent = false;
        for (final var member : members) {
            switch (member) {
                case MethodTree method -> {
                    if (method.getReturnType() == null) {
                        // constructor — check below
                        continue;
                    }
                    if (!method.getModifiers().getFlags().contains(Modifier.STATIC)) {
                        return false;
                    }
                    hasStaticContent = true;
                }
                case VariableTree field -> {
                    if (!field.getModifiers().getFlags().contains(Modifier.STATIC)) {
                        return false;
                    }
                    hasStaticContent = true;
                }
                default -> { /* nested types, etc. — ignore */ }
            }
        }
        return hasStaticContent;
    }

    private static boolean hasPrivateNoArgConstructor(final ClassTree node) {
        return node.getMembers().stream()
            .filter(m -> m instanceof MethodTree)
            .map(m -> (MethodTree) m)
            .anyMatch(m -> m.getReturnType() == null
                && m.getParameters().isEmpty()
                && m.getModifiers().getFlags().contains(Modifier.PRIVATE));
    }
}
