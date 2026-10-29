package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;
import org.junit.jupiter.api.Test;

class EnumRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source))).stripTrailing();
    }

    @Test
    void emptyEnum_rendersOnOneLine() {
        assertThat(format("enum Color {}")).isEqualTo("enum Color {}");
    }

    @Test
    void enumWithConstants_fitsOnOneLine_declarationOrderNoTrailingComma() {
        assertThat(format("enum Color { RED, GREEN, BLUE }")).isEqualTo("enum Color { RED, GREEN, BLUE }");
    }

    @Test
    void longEnum_doesNotFit_breaksToOnePerLine() {
        final var source = "enum Status { FIRST_VERY_LONG_CONSTANT, SECOND_VERY_LONG_CONSTANT, THIRD_VERY_LONG_CONSTANT, FOURTH_VERY_LONG_CONSTANT, FIFTH_VERY_LONG_CONSTANT, SIXTH_VERY_LONG_CONSTANT }";
        final var expected = "enum Status {\n\n    FIRST_VERY_LONG_CONSTANT,\n    SECOND_VERY_LONG_CONSTANT,\n    THIRD_VERY_LONG_CONSTANT,\n    FOURTH_VERY_LONG_CONSTANT,\n    FIFTH_VERY_LONG_CONSTANT,\n    SIXTH_VERY_LONG_CONSTANT\n}";
        // No trailing comma: these tests print straight from the parse, and the renderer only ever
        // emits the separator the source already has. EnumTrailingCommaTest covers the lint rule
        // that writes it in, and EnumTrailingCommaTest#wideEnumEndsUpWithATrailingComma the result.
        assertThat(format(source)).isEqualTo(expected);
    }

    @Test
    void enumWithBodyMember_separatedByBlankLine() {
        final var source = "enum Status { ACTIVE, INACTIVE; public boolean isActive() { return this == ACTIVE; } }";
        final var expected = "enum Status {\n\n    ACTIVE,\n    INACTIVE;\n\n    public boolean isActive() {\n        return this == ACTIVE;\n    }\n}";
        assertThat(format(source)).isEqualTo(expected);
    }

    @Test
    void alreadySortedEnum_isIdempotent() {
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
