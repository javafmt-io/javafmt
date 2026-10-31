package io.javafmt.lint;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LintRuleNameTest {
    @Test
    void nameDefaultsToSimpleClassName() {
        final var anonymousShape = new AvoidStarImport();
        assertThat(anonymousShape.name()).isEqualTo("AvoidStarImport");
        assertThat(new NeedBraces().name()).isEqualTo("NeedBraces");
    }
}
