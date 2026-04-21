package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GrindTest {

    @Test
    void formatEmptySourceReturnsEmptyString() {
        assertThat(Grind.format("")).isEqualTo("");
    }

    @Test
    void formatIsIdempotent() {
        final var source = "class Foo {}";
        assertThat(Grind.format(Grind.format(source))).isEqualTo(Grind.format(source));
    }
}
