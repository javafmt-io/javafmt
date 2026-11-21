package io.javafmt.lint;

interface ScanningRule extends LintRule {

    RuleScan scan(LintContext context);

    @Override
    default LintResult apply(final LintContext context) {
        final var scan = scan(context);
        scan.scanner().scan(context.unit().tree(), null);
        return scan.result().get();
    }
}
