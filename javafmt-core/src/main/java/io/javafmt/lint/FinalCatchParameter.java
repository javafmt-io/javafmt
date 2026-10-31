package io.javafmt.lint;

import com.sun.source.tree.CatchTree;
import io.javafmt.parser.ChainScanner;

import java.util.Optional;

public final class FinalCatchParameter implements LintRule {

    @Override
    public LintResult apply(final LintContext context) {
        final var unit = context.unit();
        final var reassignments = new ReassignmentDetector();
        final var catches = LintScan.<CatchTree>collect(
                context,
                sink -> new ChainScanner<>() {

                    @Override
                    public Void visitCatch(final CatchTree node, final Void p) {
                        sink.add(node);
                        return super.visitCatch(node, null);
                    }
                });
        return LintResult.ofEdits(
                catches.stream()
                        .filter(c -> !Modifiers.hasFinal(c.getParameter()))
                        .filter(c -> !reassignments.reassigned(c.getBlock(), c.getParameter().getName().toString()))
                        .flatMap(c -> Optional.of(unit.startOf(c.getParameter().getType())).filter(pos -> pos >= 0).stream())
                        .map(pos -> LintEdit.insert(pos, "final "))
                        .toList());
    }
}
