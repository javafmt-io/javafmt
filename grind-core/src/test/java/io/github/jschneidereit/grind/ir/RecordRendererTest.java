package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.printer.Printer;

import org.junit.jupiter.api.Test;

class RecordRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return Printer.print(DocBuilder.build(JavaParser.parse(source)), WIDTH);
    }

    @Test
    void emptyRecord_rendersOnOneLine() {
        assertThat(format("record Empty() {}")).isEqualTo("record Empty() {}");
    }

    @Test
    void recordWithComponents_fitsOnOneLine() {
        assertThat(format("record Pair(int x, int y) {}")).isEqualTo("record Pair(int x, int y) {}");
    }

    @Test
    void recordWithComponentsAndBodyMember_componentListStaysOnOneLine() {
        assertThat(format("record R(int x) { void go() {} }"))
            .isEqualTo("record R(int x) {\n    void go() {}\n}");
    }

    @Test
    void record_withImplements_rendersAfterComponents() {
        assertThat(format("record R(int x) implements Comparable<R> {}"))
            .isEqualTo("record R(int x) implements Comparable<R> {}");
    }

    @Test
    void recordWithManyComponents_breaksToOnePerLine() {
        final var source = "record LongRecord(FirstVeryLongComponentType firstVeryLongComponentName, SecondVeryLongComponentType secondVeryLongComponent, ThirdVeryLong thirdVeryLong) {}";
        final var expected = """
                record LongRecord(
                    FirstVeryLongComponentType firstVeryLongComponentName,
                    SecondVeryLongComponentType secondVeryLongComponent,
                    ThirdVeryLong thirdVeryLong
                ) {}""";
        assertThat(format(source)).isEqualTo(expected);
    }
}
