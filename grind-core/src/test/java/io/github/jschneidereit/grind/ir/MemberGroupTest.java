package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberGroupTest {

    @Test
    void ordinalsMatchClaudeMdSpec() {
        assertThat(MemberGroup.values()).containsExactly(
            MemberGroup.NESTED_TYPE_SEALED,
            MemberGroup.STATIC_FIELD,
            MemberGroup.STATIC_INITIALIZER,
            MemberGroup.INSTANCE_FIELD,
            MemberGroup.INSTANCE_INITIALIZER,
            MemberGroup.CONSTRUCTOR,
            MemberGroup.PUBLIC_METHOD,
            MemberGroup.PROTECTED_METHOD,
            MemberGroup.PACKAGE_METHOD,
            MemberGroup.PRIVATE_METHOD,
            MemberGroup.STATIC_METHOD,
            MemberGroup.NESTED_TYPE_NORMAL,
            MemberGroup.UNKNOWN);
    }
}
