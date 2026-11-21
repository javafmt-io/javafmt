package io.javafmt.builder;

import org.junit.jupiter.api.Test;

import static io.javafmt.builder.RendererTestSupport.format;
import static org.assertj.core.api.Assertions.assertThat;

class FieldRendererTest {

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
