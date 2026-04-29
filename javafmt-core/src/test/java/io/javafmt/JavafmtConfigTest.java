package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JavafmtConfigTest {

    @Test
    void defaults_reorderMembersIsFalse() {
        assertThat(new JavafmtConfig().reorderMembers()).isFalse();
    }
}
