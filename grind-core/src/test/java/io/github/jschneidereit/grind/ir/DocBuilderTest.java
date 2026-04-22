package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.printer.Printer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration smoke tests — one per DocBuilder dispatch path.
 * Exhaustive rendering behaviour lives in the per-renderer test classes.
 */
class DocBuilderTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return Printer.print(DocBuilder.build(JavaParser.parse(source)), WIDTH);
    }

    @Test
    void emptyClass_rendersOnOneLine() {
        assertThat(format("class Foo {}")).isEqualTo("class Foo {}");
    }

    @Test
    void emptyInterface_rendersOnOneLine() {
        assertThat(format("interface Foo {}")).isEqualTo("interface Foo {}");
    }

    @Test
    void emptyRecord_rendersOnOneLine() {
        assertThat(format("record Foo() {}")).isEqualTo("record Foo() {}");
    }

    @Test
    void emptyEnum_rendersOnOneLine() {
        assertThat(format("enum Foo {}")).isEqualTo("enum Foo {}");
    }

    @Test
    void singleImport_sortedAboveClass() {
        assertThat(format("import java.util.List;\nclass Foo {}"))
            .isEqualTo("import java.util.List;\n\nclass Foo {}");
    }

    @Test
    void field_isIndentedInsideClass() {
        assertThat(format("class Foo { int x; }"))
            .isEqualTo("class Foo {\n    int x;\n}");
    }

    @Test
    void method_isIndentedInsideClass() {
        assertThat(format("class Foo { void bar() {} }"))
            .isEqualTo("class Foo {\n    void bar() {}\n}");
    }

    @Test
    void returnStatement_renderedInsideMethod() {
        assertThat(format("class Foo { int bar() { return 42; } }"))
            .isEqualTo("class Foo {\n    int bar() {\n        return 42;\n    }\n}");
    }

    @Test
    void expressionStatement_renderedInsideMethod() {
        assertThat(format("class Foo { void bar() { doIt(); } }"))
            .isEqualTo("class Foo {\n    void bar() {\n        doIt();\n    }\n}");
    }

    @Test
    void switchExpression_renderedInsideMethod() {
        assertThat(format("class Foo { String bar(int x) { return switch (x) { case 1 -> \"one\"; default -> \"other\"; }; } }"))
            .isEqualTo("class Foo {\n    String bar(int x) {\n        return switch (x) {\n            case 1 -> \"one\";\n            default -> \"other\";\n        };\n    }\n}");
    }

    @Test
    void switchStatement_renderedInsideMethod() {
        assertThat(format("class Foo { void bar(int x) { switch (x) { case 1 -> doIt(); default -> doOther(); } } }"))
            .isEqualTo("class Foo {\n    void bar(int x) {\n        switch (x) {\n            case 1 -> doIt();\n            default -> doOther();\n        }\n    }\n}");
    }

    @Nested
    class MemberGrouping {

        @Test
        void fieldThenMethod_haveBlankLineBetweenThem() {
            assertThat(format("class Foo { int x; void bar() {} }"))
                .isEqualTo("class Foo {\n    int x;\n\n    void bar() {}\n}");
        }

        @Test
        void twoFields_haveBlankLineBetweenThem() {
            assertThat(format("class Foo { int x; int y; }"))
                .isEqualTo("class Foo {\n    int x;\n\n    int y;\n}");
        }
    }
}
