package io.javafmt.lint;

import com.sun.source.tree.Tree;

/**
 * A rule that does its work in one scan over the tree. {@link LintEngine} gathers the {@link RuleScan}
 * of every such rule and walks the tree once for all of them. Run on its own through {@link
 * #apply}, a rule walks the tree itself and gives the same result.
 *
 * <p>A rule's scanner must do all its work on a node before it calls {@code super} for the node's
 * children, since it shares the walk: see {@link io.javafmt.parser.FanOutScanner}.
 */
interface ScanningRule extends LintRule {

    RuleScan scan(LintContext context);

    @Override
    default LintResult apply(final LintContext context) {
        return runAlone(scan(context), context.unit().tree());
    }

    static LintResult runAlone(final RuleScan scan, final Tree tree) {
        scan.scanner().scan(tree, null);
        return scan.result().get();
    }
}
