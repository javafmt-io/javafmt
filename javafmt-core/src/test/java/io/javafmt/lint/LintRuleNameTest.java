package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LintRuleNameTest {

    @Test
    void nameDefaultsToSimpleClassName() {
        final LintRule anonymousShape = new AvoidStarImport();
        assertThat(anonymousShape.name()).isEqualTo("AvoidStarImport");
        assertThat(new NeedBraces().name()).isEqualTo("NeedBraces");
    }
}
