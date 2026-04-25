package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.printer.Printer;

import org.junit.jupiter.api.Test;

class FieldRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return Printer.print(DocBuilder.build(JavaParser.parseUnit(source)), WIDTH);
    }

    @Test
    void singleField_isIndentedInsideClass() {
        assertThat(format("class Foo { int x; }"))
            .isEqualTo("class Foo {\n    int x;\n}");
    }

    @Test
    void fieldWithAnnotation_rendersAnnotationInline() {
        assertThat(format("class Foo { @Getter int x; }"))
            .isEqualTo("class Foo {\n    @Getter int x;\n}");
    }

    @Test
    void fieldWithMultipleAnnotations_renderedInlineSpaceSeparated() {
        assertThat(format("class Foo { @Getter @Setter int x; }"))
            .isEqualTo("class Foo {\n    @Getter @Setter int x;\n}");
    }
}
