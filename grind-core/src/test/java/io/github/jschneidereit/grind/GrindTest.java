package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GrindTest {

    @Test
    void formatEmptySourceReturnsEmptyString() {
        assertThat(Grind.format("")).isEqualTo("");
    }

    @Test
    void formatNormalisesWhitespace() {
        assertThat(Grind.format("class Foo { int x; }"))
            .isEqualTo("class Foo {\n    int x;\n}");
    }

    @Test
    void formatIsIdempotent() {
        final var source = "class Foo { int x; }";
        assertThat(Grind.format(Grind.format(source))).isEqualTo(Grind.format(source));
    }
}
