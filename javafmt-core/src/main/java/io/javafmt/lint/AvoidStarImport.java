package io.javafmt.lint;

import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.Diagnostic;
import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Warns for non-static wildcard imports ({@code import pkg.*;}). Static wildcard imports
 * are excluded because their replacement requires enumerating static members, which is
 * handled by {@link ExplodeStarImports}.
 */
public final class AvoidStarImport implements LintRule {

    private static final String MESSAGE =
        "avoid star import; use explicit imports instead";

    @Override
    public String name() {
        return "AvoidStarImport";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var diagnostics = new ArrayList<Diagnostic>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitImport(final ImportTree node, final Void p) {
                if (!node.isStatic() && isStarImport(node)) {
                    diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(node)));
                }
                return null;
            }
        }.scan(new TreePath(unit.tree()), null);
        return new LintResult(List.of(), diagnostics);
    }

    private static boolean isStarImport(final ImportTree imp) {
        return imp.getQualifiedIdentifier() instanceof MemberSelectTree ms
            && ms.getIdentifier().contentEquals("*");
    }
}
