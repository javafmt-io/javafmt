package io.javafmt.lint;

import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.Diagnostic;
import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Warns if a {@code switch} statement has no {@code default} case. Switch expressions
 * are excluded because they require exhaustiveness from the compiler. Arrow-form switches
 * (which are always expressions in Java 14+) are also excluded.
 */
public final class MissingSwitchDefault implements LintRule {

    private static final String MESSAGE = "switch statement does not have a 'default' case";

    @Override
    public String name() {
        return "MissingSwitchDefault";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var diagnostics = new ArrayList<Diagnostic>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitSwitch(final SwitchTree node, final Void p) {
                final var hasDefault = node.getCases().stream()
                    .anyMatch(c -> c.getLabels().stream()
                        .anyMatch(l -> l instanceof DefaultCaseLabelTree));
                if (!hasDefault) {
                    diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(node)));
                }
                return super.visitSwitch(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return new LintResult(List.of(), diagnostics);
    }
}
