package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.Diagnostic;
import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Warns if a class overrides {@code equals(Object)} without overriding {@code hashCode()},
 * or vice versa. The contract of {@link Object#equals} requires a consistent
 * {@link Object#hashCode}.
 */
public final class EqualsHashCode implements LintRule {

    @Override
    public String name() {
        return "EqualsHashCode";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var diagnostics = new ArrayList<Diagnostic>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitClass(final ClassTree node, final Void p) {
                final var methods = node.getMembers().stream()
                    .filter(m -> m instanceof MethodTree)
                    .map(m -> (MethodTree) m)
                    .toList();
                final var hasEquals = methods.stream().anyMatch(EqualsHashCode::isEqualsOverride);
                final var hasHashCode = methods.stream().anyMatch(EqualsHashCode::isHashCode);
                if (hasEquals && !hasHashCode) {
                    diagnostics.add(new Diagnostic.Warning(
                        "class overrides equals(Object) but not hashCode()",
                        unit.positionOf(node)));
                } else if (!hasEquals && hasHashCode) {
                    diagnostics.add(new Diagnostic.Warning(
                        "class overrides hashCode() but not equals(Object)",
                        unit.positionOf(node)));
                }
                return super.visitClass(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return new LintResult(List.of(), diagnostics);
    }

    private static boolean isEqualsOverride(final MethodTree method) {
        if (!method.getName().contentEquals("equals")) {
            return false;
        }
        final var params = method.getParameters();
        if (params.size() != 1) {
            return false;
        }
        return params.get(0).getType() instanceof IdentifierTree id
            && id.getName().contentEquals("Object");
    }

    private static boolean isHashCode(final MethodTree method) {
        return method.getName().contentEquals("hashCode")
            && method.getParameters().isEmpty();
    }
}
