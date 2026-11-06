package io.javafmt.lint;

import java.util.Optional;

interface RewriteVerifyingRule extends LintRule {

    Optional<String> rewriteToVerify(LintContext context);
}
