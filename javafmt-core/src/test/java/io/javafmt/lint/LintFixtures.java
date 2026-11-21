package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import lombok.experimental.UtilityClass;

@UtilityClass
final class LintFixtures {

    String fix(final LintRule rule, final String source) {
        final var unit = JavaParser.parseUnit(source);
        return LintEngine.applyEdits(
                source, rule.apply(LintContext.parseOnly(unit)).edits());
    }
}
