package io.javafmt.lint;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.Diagnostic;
import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Warns when a class defines {@code equals(SpecificType)} (an overload) instead of the
 * required {@code equals(Object)} override. Covariant overloads are silently ignored by
 * collections and polymorphic code, making them almost always a latent bug.
 */
public final class CovariantEquals implements LintRule {

    private static final String MESSAGE =
        "covariant equals(SpecificType) overload; use equals(Object) to override correctly";

    @Override
    public String name() {
        return "CovariantEquals";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var diagnostics = new ArrayList<Diagnostic>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitMethod(final MethodTree node, final Void p) {
                if (isCovariantEquals(node)) {
                    diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(node)));
                }
                return super.visitMethod(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return new LintResult(List.of(), diagnostics);
    }

    private static boolean isCovariantEquals(final MethodTree method) {
        if (!method.getName().contentEquals("equals")) {
            return false;
        }
        final var params = method.getParameters();
        if (params.size() != 1) {
            return false;
        }
        // If parameter type is 'Object', it's an override, not a covariant overload
        return !(params.get(0).getType() instanceof IdentifierTree id
            && id.getName().contentEquals("Object"));
    }
}
