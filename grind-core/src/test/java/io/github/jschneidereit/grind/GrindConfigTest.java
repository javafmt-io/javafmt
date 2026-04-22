package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GrindConfigTest {

    @Test
    void defaults_reorderMembersIsFalse() {
        assertThat(new GrindConfig().reorderMembers()).isFalse();
    }
}
