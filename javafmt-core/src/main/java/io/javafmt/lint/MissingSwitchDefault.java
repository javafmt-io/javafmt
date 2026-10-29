package io.javafmt.lint;

import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.SwitchTree;
import io.javafmt.Diagnostic;
import io.javafmt.parser.ChainPathScanner;

/**
 * Warns if a {@code switch} statement has no {@code default} case. Switch expressions never reach
 * this rule — {@code visitSwitch} sees statements only — because the compiler already requires them
 * to be exhaustive.
 *
 * <p>A pattern switch over a sealed type is excluded for the same reason: since Java 21 the
 * compiler checks those for exhaustiveness too, and adding a {@code default} to one is how you stop
 * it telling you about the subtype you forgot to handle. Arrow form has nothing to do with it —
 * that was what this javadoc used to claim, and arrow-form switch *statements* are ordinary
 * switches like any other.
 */
public final class MissingSwitchDefault implements LintRule {

    private static final String MESSAGE = "switch statement does not have a 'default' case";

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.diagnostics(
                ctx,
                diagnostics -> new ChainPathScanner<>() {

                    @Override
                    public Void visitSwitch(final SwitchTree node, final Void p) {
                        final var hasDefault = node.getCases()
                                .stream()
                                .anyMatch(c -> c.getLabels().stream().anyMatch(l -> l instanceof DefaultCaseLabelTree));
                        if (!hasDefault && !allLabelsArePatterns(node)) {
                            diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(node)));
                        }
                        return super.visitSwitch(node, null);
                    }
                });
    }

    /**
     * Whether every arm matches on a pattern, which is what makes a switch one the compiler checks
     * for exhaustiveness. A switch with no arms at all is not one of those.
     */
    private static boolean allLabelsArePatterns(final SwitchTree node) {
        return !node.getCases().isEmpty()
            && node.getCases()
                    .stream()
                    .allMatch(c -> !c.getLabels().isEmpty() && c.getLabels().stream().allMatch(PatternCaseLabelTree.class::isInstance));
    }
}
