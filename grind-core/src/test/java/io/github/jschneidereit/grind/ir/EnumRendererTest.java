package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.printer.Printer;

import org.junit.jupiter.api.Test;

class EnumRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return Printer.print(DocBuilder.build(JavaParser.parse(source)), WIDTH);
    }

    @Test
    void emptyEnum_rendersOnOneLine() {
        assertThat(format("enum Color {}")).isEqualTo("enum Color {}");
    }

    @Test
    void enumWithConstants_fitsOnOneLine_sortedNoTrailingComma() {
        assertThat(format("enum Color { RED, GREEN, BLUE }"))
            .isEqualTo("enum Color { BLUE, GREEN, RED }");
    }

    @Test
    void longEnum_doesNotFit_breaksToOnePerLineWithTrailingComma() {
        final var source = "enum Status { FIRST_VERY_LONG_CONSTANT, SECOND_VERY_LONG_CONSTANT, THIRD_VERY_LONG_CONSTANT, FOURTH_VERY_LONG_CONSTANT, FIFTH_VERY_LONG_CONSTANT, SIXTH_VERY_LONG_CONSTANT }";
        final var expected = "enum Status {\n    FIFTH_VERY_LONG_CONSTANT,\n    FIRST_VERY_LONG_CONSTANT,\n    FOURTH_VERY_LONG_CONSTANT,\n    SECOND_VERY_LONG_CONSTANT,\n    SIXTH_VERY_LONG_CONSTANT,\n    THIRD_VERY_LONG_CONSTANT,\n}";
        assertThat(format(source)).isEqualTo(expected);
    }

    @Test
    void enumWithBodyMember_separatedByBlankLine() {
        final var source = "enum Status { ACTIVE, INACTIVE; public boolean isActive() { return this == ACTIVE; } }";
        final var expected = "enum Status {\n    ACTIVE,\n    INACTIVE;\n\n    public boolean isActive() {\n        return this == ACTIVE;\n    }\n}";
        assertThat(format(source)).isEqualTo(expected);
    }

    @Test
    void alreadySortedEnum_isIdempotent() {
        final var input = "enum Color { BLUE, GREEN, RED }";
        assertThat(format(format(input))).isEqualTo(format(input));
    }
}
