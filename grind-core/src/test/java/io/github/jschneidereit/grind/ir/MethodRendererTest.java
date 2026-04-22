package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.printer.Printer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MethodRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return Printer.print(DocBuilder.build(JavaParser.parse(source)), WIDTH);
    }

    @Test
    void emptyBody_rendersOnOneLine() {
        assertThat(format("class Foo { void bar() {} }"))
            .isEqualTo("class Foo {\n    void bar() {}\n}");
    }

    @Test
    void withReturnStatement_rendersBody() {
        assertThat(format("class Foo { int bar() { return 42; } }"))
            .isEqualTo("class Foo {\n    int bar() {\n        return 42;\n    }\n}");
    }

    @Test
    void withExpressionStatement_rendersBody() {
        assertThat(format("class Foo { void bar() { System.out.println(42); } }"))
            .isEqualTo("class Foo {\n    void bar() {\n        System.out.println(42);\n    }\n}");
    }

    @Test
    void withLocalVariable_rendersBody() {
        assertThat(format("class Foo { int bar() { int x = 1; return x; } }"))
            .isEqualTo("class Foo {\n    int bar() {\n        int x = 1;\n        return x;\n    }\n}");
    }

    @Nested
    class Annotations {

        @Test
        void singleParameterless_rendersInline() {
            assertThat(format("class Foo { @Override void bar() {} }"))
                .isEqualTo("class Foo {\n    @Override void bar() {}\n}");
        }

        @Test
        void singleParameterized_rendersOnOwnLine() {
            assertThat(format("class Foo { @SuppressWarnings(\"all\") void bar() {} }"))
                .isEqualTo("class Foo {\n    @SuppressWarnings(\"all\")\n    void bar() {}\n}");
        }

        @Test
        void multiple_eachOnOwnLine() {
            assertThat(format("class Foo { @Deprecated @Override void bar() {} }"))
                .isEqualTo("class Foo {\n    @Deprecated\n    @Override\n    void bar() {}\n}");
        }

        @Test
        void namedArg_rendersOnOwnLine() {
            assertThat(format("class Foo { @Getter(lazy = true) int bar() { return 42; } }"))
                .isEqualTo("class Foo {\n    @Getter(lazy = true)\n    int bar() {\n        return 42;\n    }\n}");
        }
    }
}
