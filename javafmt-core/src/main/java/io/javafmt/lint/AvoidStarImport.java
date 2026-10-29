package io.javafmt.lint;

import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import io.javafmt.Diagnostic;
import io.javafmt.parser.ChainPathScanner;

/**
 * Warns for non-static wildcard imports ({@code import pkg.*;}). Static wildcard imports
 * are excluded because their replacement requires enumerating static members, which is
 * handled by {@link ExplodeStarImports}.
 */
public final class AvoidStarImport implements LintRule {

    private static final String MESSAGE = "avoid star import; use explicit imports instead";

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.diagnostics(
                ctx,
                diagnostics -> new ChainPathScanner<>() {

                    @Override
                    public Void visitImport(final ImportTree node, final Void p) {
                        if (!node.isStatic() && isStarImport(node)) {
                            diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(node)));
                        }
                        return null;
                    }
                });
    }

    private static boolean isStarImport(final ImportTree imp) {
        return imp.getQualifiedIdentifier() instanceof MemberSelectTree ms && ms.getIdentifier().contentEquals("*");
    }
}
