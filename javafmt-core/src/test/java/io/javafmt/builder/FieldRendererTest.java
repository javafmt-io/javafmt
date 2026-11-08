package io.javafmt.builder;

import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source)))
                .stripTrailing();
    }

    @Test
    void singleField_isIndentedInsideClass() {
        assertThat(format("class Foo { int x; }")).isEqualTo("class Foo {\n\n    int x;\n}");
    }

    @Test
    void fieldWithAnnotation_rendersAnnotationOnItsOwnLine() {
        assertThat(format("class Foo { @Getter int x; }")).isEqualTo("class Foo {\n\n    @Getter\n    int x;\n}");
    }

    @Test
    void fieldWithMultipleAnnotations_eachOnItsOwnLine() {
        assertThat(format("class Foo { @Getter @Setter int x; }")).isEqualTo("class Foo {\n\n    @Getter\n    @Setter\n    int x;\n}");
    }
}
