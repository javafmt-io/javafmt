package io.javafmt.builder;

import org.junit.jupiter.api.Test;

import static io.javafmt.builder.RendererTestSupport.format;
import static org.assertj.core.api.Assertions.assertThat;

class EnumRendererTest {

    @Test
    void emptyEnum_rendersOnOneLine() {
        assertThat(format("enum Color {}")).isEqualTo("enum Color {}");
    }

    @Test
    void enumWithConstants_fitsOnOneLine_keepsOrderWithoutTrailingComma() {
        assertThat(format("enum Color { RED, GREEN, BLUE }")).isEqualTo("enum Color { RED, GREEN, BLUE }");
    }

    @Test
    void longEnum_doesNotFit_breaksToOnePerLine() {
        final var source = "enum Status { FIRST_VERY_LONG_CONSTANT, SECOND_VERY_LONG_CONSTANT, THIRD_VERY_LONG_CONSTANT, FOURTH_VERY_LONG_CONSTANT, FIFTH_VERY_LONG_CONSTANT, SIXTH_VERY_LONG_CONSTANT }";
        final var expected = "enum Status {\n\n    FIRST_VERY_LONG_CONSTANT,\n    SECOND_VERY_LONG_CONSTANT,\n    THIRD_VERY_LONG_CONSTANT,\n    FOURTH_VERY_LONG_CONSTANT,\n    FIFTH_VERY_LONG_CONSTANT,\n    SIXTH_VERY_LONG_CONSTANT,\n}";
        assertThat(format(source)).isEqualTo(expected);
    }

    @Test
    void enumWithBodyMember_separatedByBlankLine() {
        final var source = "enum Status { ACTIVE, INACTIVE; public boolean isActive() { return this == ACTIVE; } }";
        final var expected = "enum Status {\n\n    ACTIVE,\n    INACTIVE;\n\n    public boolean isActive() {return this == ACTIVE;}\n}";
        assertThat(format(source)).isEqualTo(expected);
    }

    @Test
    void formattedEnum_isIdempotent() {
        final var input = "enum Color { BLUE, GREEN, RED }";
        assertThat(format(format(input))).isEqualTo(format(input));
    }

    @Test
    void enum_withImplements_rendersAfterName() {
        assertThat(format("enum Status implements Serializable { ACTIVE }")).isEqualTo("enum Status implements Serializable { ACTIVE }");
    }

    @Test
    void enum_withNestedClass_stillRenders() {
        final var source = "enum Status { ACTIVE; class Helper {} }";
        final var expected = "enum Status {\n\n    ACTIVE;\n\n    class Helper {}\n}";
        assertThat(format(source)).isEqualTo(expected);
    }
}
