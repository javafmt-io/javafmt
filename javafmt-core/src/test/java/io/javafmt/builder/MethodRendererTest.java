package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MethodRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source)));
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
    class ParamList {

        @Test
        void fitsFlat_staysOnOneLine() {
            assertThat(format("class Foo { void bar(int a, int b) {} }"))
                .isEqualTo("class Foo {\n    void bar(int a, int b) {}\n}");
        }

        @Test
        void emptyParams_noBreak() {
            assertThat(format("class Foo { void bar() {} }"))
                .isEqualTo("class Foo {\n    void bar() {}\n}");
        }

        @Test
        void doesNotFit_breaksAllParams() {
            final var source = "class Foo { void someMethod("
                + "String argumentNumberOne, String argumentNumberTwo, String argumentNumberThree, "
                + "String argumentNumberFour, String argumentNumberFive) {} }";
            assertThat(format(source)).isEqualTo(
                """
                class Foo {
                    void someMethod(
                        String argumentNumberOne,
                        String argumentNumberTwo,
                        String argumentNumberThree,
                        String argumentNumberFour,
                        String argumentNumberFive
                    ) {}
                }""");
        }

        @Test
        void abstractMethod_breaksWithSemicolon() {
            final var source = "interface Foo { void someMethod("
                + "String argumentNumberOne, String argumentNumberTwo, String argumentNumberThree, "
                + "String argumentNumberFour, String argumentNumberFive); }";
            assertThat(format(source)).isEqualTo(
                """
                interface Foo {
                    void someMethod(
                        String argumentNumberOne,
                        String argumentNumberTwo,
                        String argumentNumberThree,
                        String argumentNumberFour,
                        String argumentNumberFive
                    );
                }""");
        }
    }

    @Nested
    class Throws {

        @Test
        void singleType_rendersAfterParens() {
            assertThat(format("class Foo { void bar() throws IOException {} }"))
                .isEqualTo("class Foo {\n    void bar() throws IOException {}\n}");
        }

        @Test
        void multipleTypes_flatWithCommas() {
            assertThat(format("class Foo { void bar() throws A, B, C {} }"))
                .isEqualTo("class Foo {\n    void bar() throws A, B, C {}\n}");
        }

        @Test
        void noThrows_unchanged() {
            assertThat(format("class Foo { void bar(int x) { return; } }"))
                .isEqualTo("class Foo {\n    void bar(int x) {\n        return;\n    }\n}");
        }

        @Test
        void longClauseForcesSignatureBreak() {
            final var source = "class Foo { void someMethod(int a, int b) throws "
                + "ExceptionAlpha, ExceptionBeta, ExceptionGamma, ExceptionDelta, "
                + "ExceptionEpsilon, ExceptionZeta, ExceptionEta, ExceptionTheta {} }";
            assertThat(format(source)).isEqualTo(
                """
                class Foo {
                    void someMethod(
                        int a,
                        int b
                    )
                        throws ExceptionAlpha, ExceptionBeta, ExceptionGamma, ExceptionDelta, ExceptionEpsilon, ExceptionZeta, ExceptionEta, ExceptionTheta {}
                }""");
        }

        @Test
        void veryLongClauseBreaksThrowsList() {
            final var source = "class Foo { void someMethod(int a, int b) throws "
                + "ExceptionAlpha, ExceptionBeta, ExceptionGamma, ExceptionDelta, "
                + "ExceptionEpsilon, ExceptionZeta, ExceptionEta, ExceptionTheta, "
                + "ExceptionIota, ExceptionKappa, ExceptionLambda, ExceptionMu, ExceptionNu {} }";
            assertThat(format(source)).isEqualTo(
                """
                class Foo {
                    void someMethod(
                        int a,
                        int b
                    )
                        throws ExceptionAlpha,
                            ExceptionBeta,
                            ExceptionGamma,
                            ExceptionDelta,
                            ExceptionEpsilon,
                            ExceptionZeta,
                            ExceptionEta,
                            ExceptionTheta,
                            ExceptionIota,
                            ExceptionKappa,
                            ExceptionLambda,
                            ExceptionMu,
                            ExceptionNu {}
                }""");
        }

        @Test
        void abstractMethod_rendersBeforeSemicolon() {
            assertThat(format("interface Foo { void bar() throws IOException; }"))
                .isEqualTo("interface Foo {\n    void bar() throws IOException;\n}");
        }
    }

    @Nested
    class TypeParams {

        @Test
        void single_rendersBeforeReturnType() {
            assertThat(format("class Foo { <T> T bar(T x) { return x; } }"))
                .isEqualTo("class Foo {\n    <T> T bar(T x) {\n        return x;\n    }\n}");
        }

        @Test
        void multiple_commaSeparated() {
            assertThat(format("class Foo { <K, V> Map<K, V> bar() { return null; } }"))
                .isEqualTo("class Foo {\n    <K, V> Map<K, V> bar() {\n        return null;\n    }\n}");
        }

        @Test
        void withBounds_rendersInline() {
            assertThat(format("class Foo { <T extends Comparable<T>> T max(T a, T b) {} }"))
                .isEqualTo("class Foo {\n    <T extends Comparable<T>> T max(T a, T b) {}\n}");
        }

        @Test
        void noTypeParams_unchanged() {
            assertThat(format("class Foo { int bar() { return 1; } }"))
                .isEqualTo("class Foo {\n    int bar() {\n        return 1;\n    }\n}");
        }

        @Test
        void longList_breaksTypeParams() {
            final var source = "class Foo { <"
                + "TypeParameterAlpha extends Comparable<TypeParameterAlpha>, "
                + "TypeParameterBeta extends Comparable<TypeParameterBeta>, "
                + "TypeParameterGamma extends Comparable<TypeParameterGamma>, "
                + "TypeParameterDelta extends Comparable<TypeParameterDelta>"
                + "> int bar() { return 0; } }";
            assertThat(format(source)).isEqualTo(
                """
                class Foo {
                    <
                        TypeParameterAlpha extends Comparable<TypeParameterAlpha>,
                        TypeParameterBeta extends Comparable<TypeParameterBeta>,
                        TypeParameterGamma extends Comparable<TypeParameterGamma>,
                        TypeParameterDelta extends Comparable<TypeParameterDelta>
                    > int bar() {
                        return 0;
                    }
                }""");
        }

        @Test
        void withAnnotations_rendersAfterAnnotations() {
            assertThat(format("class Foo { @Override <T> T bar(T x) { return x; } }"))
                .isEqualTo("class Foo {\n    @Override <T> T bar(T x) {\n        return x;\n    }\n}");
        }
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

    @Nested
    class Constructors {

        @Test
        void noArgs_rendersWithClassName() {
            assertThat(format("class Foo { Foo() {} }"))
                .isEqualTo("class Foo {\n    Foo() {}\n}");
        }

        @Test
        void withParams_rendersLikeMethod() {
            assertThat(format("class Foo { Foo(int x) { this.x = x; } }"))
                .isEqualTo("class Foo {\n    Foo(int x) {\n        this.x = x;\n    }\n}");
        }

        @Test
        void withModifiers() {
            assertThat(format("class Foo { public Foo() {} }"))
                .isEqualTo("class Foo {\n    public Foo() {}\n}");
        }

        @Test
        void withSuperCall() {
            assertThat(format("class Foo { Foo(int x) { super(x); } }"))
                .isEqualTo("class Foo {\n    Foo(int x) {\n        super(x);\n    }\n}");
        }

        @Test
        void withThisCall() {
            assertThat(format("class Foo { Foo() { this(0); } Foo(int x) {} }"))
                .isEqualTo("class Foo {\n    Foo() {\n        this(0);\n    }\n\n    Foo(int x) {}\n}");
        }

        @Test
        void longParamList_breaks() {
            final var source = "class Foo { Foo("
                + "String argumentNumberOne, String argumentNumberTwo, String argumentNumberThree, "
                + "String argumentNumberFour, String argumentNumberFive, String argumentNumberSix) {} }";
            assertThat(format(source)).isEqualTo(
                """
                class Foo {
                    Foo(
                        String argumentNumberOne,
                        String argumentNumberTwo,
                        String argumentNumberThree,
                        String argumentNumberFour,
                        String argumentNumberFive,
                        String argumentNumberSix
                    ) {}
                }""");
        }

        @Test
        void withAnnotations_rendersInline() {
            assertThat(format("class Foo { @Inject public Foo() {} }"))
                .isEqualTo("class Foo {\n    @Inject public Foo() {}\n}");
        }
    }
}
