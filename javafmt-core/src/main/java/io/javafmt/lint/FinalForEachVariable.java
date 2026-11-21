package io.javafmt.lint;

import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.Optional;

public final class FinalForEachVariable implements ScanningRule {

    @Override
    public RuleScan scan(final LintContext context) {
        final var unit = context.unit();
        final var reassignments = new ReassignmentDetector();
        return RuleScan.editing(edits -> new ChainScanner<>() {
            @Override
            public Void visitEnhancedForLoop(final EnhancedForLoopTree node, final Void p) {
                editFor(node, unit, reassignments).ifPresent(edits::add);
                return super.visitEnhancedForLoop(node, null);
            }
        });
    }

    private static Optional<LintEdit> editFor(final EnhancedForLoopTree loop, final ParsedUnit unit, final ReassignmentDetector reassignments) {
        final var variable = loop.getVariable();
        if (Modifiers.hasFinal(variable)
            || reassignments.reassigned(loop.getStatement(), variable.getName().toString())) {
            return Optional.empty();
        }
        return insertionPoint(unit, variable).map(pos -> LintEdit.insert(pos, "final "));
    }

    private static Optional<Integer> insertionPoint(final ParsedUnit unit, final VariableTree variable) {
        final var typeStart = unit.startOf(variable.getType());
        if (typeStart >= 0) {
            return Optional.of(typeStart);
        }
        return Optional.of(unit.startOf(variable))
                .filter(pos -> pos >= 0)
                .filter(_ -> variable.getModifiers().getAnnotations().isEmpty());
    }
}
