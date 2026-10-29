package io.javafmt.lint;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePathScanner;
import io.javafmt.Diagnostic;

/**
 * Warns when a class defines {@code equals(SpecificType)} (an overload) instead of the
 * required {@code equals(Object)} override. Covariant overloads are silently ignored by
 * collections and polymorphic code, making them almost always a latent bug.
 */
public final class CovariantEquals implements LintRule {

    private static final String MESSAGE = "covariant equals(SpecificType) overload; use equals(Object) to override correctly";

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.diagnostics(
                ctx,
                diagnostics -> new TreePathScanner<>() {

                    @Override
                    public Void visitMethod(final MethodTree node, final Void p) {
                        if (isCovariantEquals(node)) {
                            diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(node)));
                        }
                        return super.visitMethod(node, null);
                    }
                });
    }

    private static boolean isCovariantEquals(final MethodTree method) {
        if (!method.getName().contentEquals("equals")) {
            return false;
        }
        final var params = method.getParameters();
        if (params.size() != 1) {
            return false;
        }
        return !(params.getFirst().getType() instanceof IdentifierTree id && id.getName().contentEquals("Object"));
    }
}
