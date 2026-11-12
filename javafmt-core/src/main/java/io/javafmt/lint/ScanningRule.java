package io.javafmt.lint;

import com.sun.source.tree.Tree;

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
