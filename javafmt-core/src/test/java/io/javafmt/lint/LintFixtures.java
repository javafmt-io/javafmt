package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import lombok.experimental.UtilityClass;

@UtilityClass
final class LintFixtures {

    LintResult apply(final LintRule rule, final String source) {
        return rule.apply(LintContext.parseOnly(JavaParser.parseUnit(source)));
    }

    String fix(final LintRule rule, final String source) {
        return LintEngine.applyEdits(source, apply(rule, source).edits());
    }
}
