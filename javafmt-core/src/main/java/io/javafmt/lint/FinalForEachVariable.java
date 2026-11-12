package io.javafmt.lint;

import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.List;
import java.util.Optional;

public final class FinalForEachVariable implements ScanningRule {

    @Override
    public RuleScan scan(final LintContext context) {
        final var unit = context.unit();
        final var reassignments = new ReassignmentDetector();
        return RuleScan.collecting(
                (List<EnhancedForLoopTree> sink) -> new ChainScanner<>() {
                    @Override
                    public Void visitEnhancedForLoop(final EnhancedForLoopTree node, final Void p) {
                        sink.add(node);
                        return super.visitEnhancedForLoop(node, null);
                    }
                },
                loops -> LintResult.ofEdits(loops.stream()
                        .filter(l -> !Modifiers.hasFinal(l.getVariable()))
                        .filter(l -> !reassignments.reassigned(
                                l.getStatement(),
                                l.getVariable().getName().toString()))
                        .flatMap(l -> insertionPoint(unit, l.getVariable()).stream())
                        .map(pos -> LintEdit.insert(pos, "final "))
                        .toList()));
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
