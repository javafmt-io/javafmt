package io.javafmt.lint;

import com.sun.source.tree.CatchTree;
import io.javafmt.parser.ChainScanner;

import java.util.List;
import java.util.Optional;

public final class FinalCatchParameter implements ScanningRule {

    @Override
    public RuleScan scan(final LintContext context) {
        final var unit = context.unit();
        final var reassignments = new ReassignmentDetector();
        return RuleScan.collecting(
                (List<CatchTree> sink) -> new ChainScanner<>() {
                    @Override
                    public Void visitCatch(final CatchTree node, final Void p) {
                        sink.add(node);
                        return super.visitCatch(node, null);
                    }
                },
                catches -> LintResult.ofEdits(editsFor(catches, unit, reassignments)));
    }

    private static List<LintEdit> editsFor(final List<CatchTree> catches,
                                           final io.javafmt.parser.ParsedUnit unit,
                                           final ReassignmentDetector reassignments) {
        return catches.stream()
                .filter(c -> !Modifiers.hasFinal(c.getParameter()))
                .filter(c -> !reassignments.reassigned(
                        c.getBlock(), c.getParameter().getName().toString()))
                .flatMap(c -> Optional.of(unit.startOf(c.getParameter().getType()))
                        .filter(pos -> pos >= 0)
                        .stream())
                .map(pos -> LintEdit.insert(pos, "final "))
                .toList();
    }
}
