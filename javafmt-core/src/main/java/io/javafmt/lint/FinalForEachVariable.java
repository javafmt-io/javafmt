package io.javafmt.lint;

import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.Optional;

public final class FinalForEachVariable implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        final var reassignments = new ReassignmentDetector();
        final var loops = LintScan.<EnhancedForLoopTree>collect(
                ctx,
                sink -> new ChainScanner<>() {

                    @Override
                    public Void visitEnhancedForLoop(final EnhancedForLoopTree node, final Void p) {
                        sink.add(node);
                        return super.visitEnhancedForLoop(node, null);
                    }
                });
        return LintResult.ofEdits(
                loops.stream()
                        .filter(l -> !Modifiers.hasFinal(l.getVariable()))
                        .filter(l -> !reassignments.reassigned(l.getStatement(), l.getVariable().getName().toString()))
                        .flatMap(l -> insertionPoint(unit, l.getVariable()).stream())
                        .map(pos -> LintEdit.insert(pos, "final "))
                        .toList());
    }

    private static Optional<Integer> insertionPoint(final ParsedUnit unit, final VariableTree variable) {
        final var typeStart = unit.startOf(variable.getType());
        if (typeStart >= 0) {
            return Optional.of(typeStart);
        }
        return Optional.of(unit.startOf(variable)).filter(pos -> pos >= 0).filter(_ -> variable.getModifiers().getAnnotations().isEmpty());
    }
}
