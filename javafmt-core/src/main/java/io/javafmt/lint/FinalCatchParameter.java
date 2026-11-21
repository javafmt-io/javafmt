package io.javafmt.lint;

import com.sun.source.tree.CatchTree;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.Optional;

public final class FinalCatchParameter implements ScanningRule {

    @Override
    public RuleScan scan(final LintContext context) {
        final var unit = context.unit();
        final var reassignments = new ReassignmentDetector();
        return RuleScan.editing(edits -> new ChainScanner<>() {
            @Override
            public Void visitCatch(final CatchTree node, final Void p) {
                editFor(node, unit, reassignments).ifPresent(edits::add);
                return super.visitCatch(node, null);
            }
        });
    }

    private static Optional<LintEdit> editFor(final CatchTree node, final ParsedUnit unit, final ReassignmentDetector reassignments) {
        final var parameter = node.getParameter();
        if (Modifiers.hasFinal(parameter)
            || reassignments.reassigned(node.getBlock(), parameter.getName().toString())) {
            return Optional.empty();
        }
        return Optional.of(unit.startOf(parameter.getType()))
                .filter(pos -> pos >= 0)
                .map(pos -> LintEdit.insert(pos, "final "));
    }
}
