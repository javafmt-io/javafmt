package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    @ParameterizedTest(name = "blank {1}")
    @MethodSource("blankInputs")
    void formatOnBlankInputIsIdempotent(final String input, final String label) {
        final var first = Grind.format(input);
        assertThat(Grind.format(first)).as("idempotent for %s", label).isEqualTo(first);
    }

    static Stream<Arguments> blankInputs() {
        return Stream.of(
            Arguments.of("", "empty"),
            Arguments.of(" ", "single space"),
            Arguments.of("\t", "single tab"),
            Arguments.of("\n\n\n", "blank lines"));
    }
}
