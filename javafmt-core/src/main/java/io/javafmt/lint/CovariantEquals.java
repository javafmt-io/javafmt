package io.javafmt.lint;

import com.sun.source.tree.MethodTree;
import io.javafmt.Diagnostic;
import java.util.List;

/**
 * Warns when a class defines {@code equals(SpecificType)} (an overload) instead of the
 * required {@code equals(Object)} override. Covariant overloads are silently ignored by
 * collections and polymorphic code, making them almost always a latent bug.
 */
public final class CovariantEquals implements WarningRule {

    private static final String MESSAGE = "covariant equals(SpecificType) overload; use equals(Object) to override correctly";

    @Override
    public List<Diagnostic> warnings(final LintContext ctx) {
        return WarningRule.onNode(ctx, MethodTree.class, m -> Declarations.isOneArgumentEquals(m) && !Declarations.isEqualsObject(m), MESSAGE);
    }
}
