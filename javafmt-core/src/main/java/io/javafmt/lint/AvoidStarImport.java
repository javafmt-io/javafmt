package io.javafmt.lint;

import io.javafmt.Diagnostic;
import java.util.List;

/**
 * Warns for non-static wildcard imports ({@code import pkg.*;}). Static wildcard imports
 * are excluded because their replacement requires enumerating static members, which is
 * handled by {@link ExplodeStarImports}.
 */
public final class AvoidStarImport implements WarningRule {

    private static final String MESSAGE = "avoid star import; use explicit imports instead";

    @Override
    public List<Diagnostic> warnings(final LintContext ctx) {
        final var unit = ctx.unit();
        return unit.tree()
                .getImports()
                .stream()
                .filter(i -> !i.isStatic() && Declarations.isStarImport(i))
                .<Diagnostic>map(i -> new Diagnostic.Warning(MESSAGE, unit.positionOf(i)))
                .toList();
    }
}
