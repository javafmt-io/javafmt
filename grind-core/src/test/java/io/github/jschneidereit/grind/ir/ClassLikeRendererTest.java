package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.GrindConfig;
import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.printer.Printer;

import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClassLikeRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return Printer.print(DocBuilder.build(JavaParser.parse(source)), WIDTH);
    }

    private static String format(final String source, final GrindConfig config) {
        return Printer.print(DocBuilder.build(JavaParser.parse(source), config), WIDTH);
    }

    @Nested
    class EmptyBody {

        @ParameterizedTest
        @ValueSource(strings = {"class Foo {}", "class Foo {   }", "class Foo {\n\n}", "class Foo {\n    \n}"})
        void emptyClass_noPackage_rendersOnOneLine(final String source) {
            assertThat(format(source)).isEqualTo("class Foo {}");
        }

        @ParameterizedTest
        @MethodSource("packageVariants")
        void packageDeclaration_normalizesToSingleNewlineThenClass(
                final String pkg, final String expected) {
            assertThat(format("package " + pkg + ";\n\n\n\nclass Foo {}")).isEqualTo(expected);
        }

        static Stream<Arguments> packageVariants() {
            return Stream.of(
                Arguments.of("a",                         "package a;\nclass Foo {}"),
                Arguments.of("foo.bar",                   "package foo.bar;\nclass Foo {}"),
                Arguments.of("com.example.deeply.nested", "package com.example.deeply.nested;\nclass Foo {}")
            );
        }
    }

    @Nested
    class Annotations {

        @Test
        void classWithAnnotation_rendersOnOwnLineAboveDeclaration() {
            assertThat(format("@Accessors(fluent = true) class Foo {}"))
                .isEqualTo("@Accessors(fluent = true)\nclass Foo {}");
        }

        @Test
        void classWithMultipleAnnotations_eachOnOwnLine() {
            assertThat(format("@Accessors(fluent = true) @Deprecated class Foo {}"))
                .isEqualTo("@Accessors(fluent = true)\n@Deprecated\nclass Foo {}");
        }
    }

    @Nested
    class InterfaceDeclaration {

        @Test
        void emptyInterface_rendersOnOneLine() {
            assertThat(format("interface Foo {}")).isEqualTo("interface Foo {}");
        }

        @Test
        void interfaceWithAbstractMethod_rendersSignatureWithSemicolon() {
            assertThat(format("interface Foo { void bar(); }"))
                .isEqualTo("interface Foo {\n    void bar();\n}");
        }

        @Test
        void interfaceWithDefaultMethod_rendersBodyLikeClassMethod() {
            assertThat(format("interface Foo { default void bar() { System.out.println(); } }"))
                .isEqualTo("interface Foo {\n    default void bar() {\n        System.out.println();\n    }\n}");
        }

        @Test
        void interfaceWithConstantField_rendersField() {
            assertThat(format("interface Foo { public static final int MAX = 100; }"))
                .isEqualTo("interface Foo {\n    public static final int MAX = 100;\n}");
        }
    }

    @Nested
    class Supertypes {

        @Test
        void class_withExtends_rendersSuperclass() {
            assertThat(format("class Foo extends Bar {}"))
                .isEqualTo("class Foo extends Bar {}");
        }

        @Test
        void class_withImplementsSingle_rendersInterface() {
            assertThat(format("class Foo implements Runnable {}"))
                .isEqualTo("class Foo implements Runnable {}");
        }

        @Test
        void class_withImplementsMultiple_commaSeparated() {
            assertThat(format("class Foo implements A, B, C {}"))
                .isEqualTo("class Foo implements A, B, C {}");
        }

        @Test
        void class_withExtendsAndImplements_orderedCorrectly() {
            assertThat(format("class Foo extends Bar implements A, B {}"))
                .isEqualTo("class Foo extends Bar implements A, B {}");
        }

        @Test
        void interface_withExtendsSingle_usesExtendsKeyword() {
            assertThat(format("interface Foo extends Bar {}"))
                .isEqualTo("interface Foo extends Bar {}");
        }

        @Test
        void interface_withExtendsMultiple_commaSeparated() {
            assertThat(format("interface Foo extends A, B, C {}"))
                .isEqualTo("interface Foo extends A, B, C {}");
        }

        @Test
        void class_withGenericSuperclass_rendersOpaqueType() {
            assertThat(format("class Foo extends Bar<String, Integer> {}"))
                .isEqualTo("class Foo extends Bar<String, Integer> {}");
        }

        @Test
        void class_withLongImplementsList_breaks() {
            final var source = "class FooBarBaz implements "
                + "InterfaceAlpha, InterfaceBeta, InterfaceGamma, InterfaceDelta, "
                + "InterfaceEpsilon, InterfaceZeta, InterfaceEta, InterfaceTheta, "
                + "InterfaceIota, InterfaceKappa, InterfaceLambda, InterfaceMu {}";
            assertThat(format(source)).isEqualTo(
                """
                class FooBarBaz
                    implements InterfaceAlpha,
                        InterfaceBeta,
                        InterfaceGamma,
                        InterfaceDelta,
                        InterfaceEpsilon,
                        InterfaceZeta,
                        InterfaceEta,
                        InterfaceTheta,
                        InterfaceIota,
                        InterfaceKappa,
                        InterfaceLambda,
                        InterfaceMu {}""");
        }

        @Test
        void class_withExtendsAndLongImplementsList_breaks() {
            final var source = "class FooBarBaz extends SuperBar implements "
                + "InterfaceAlpha, InterfaceBeta, InterfaceGamma, InterfaceDelta, "
                + "InterfaceEpsilon, InterfaceZeta, InterfaceEta, InterfaceTheta, "
                + "InterfaceIota, InterfaceKappa, InterfaceLambda {}";
            assertThat(format(source)).isEqualTo(
                """
                class FooBarBaz
                    extends SuperBar
                    implements InterfaceAlpha,
                        InterfaceBeta,
                        InterfaceGamma,
                        InterfaceDelta,
                        InterfaceEpsilon,
                        InterfaceZeta,
                        InterfaceEta,
                        InterfaceTheta,
                        InterfaceIota,
                        InterfaceKappa,
                        InterfaceLambda {}""");
        }
    }

    @Nested
    class NestedTypes {

        @Test
        void class_withNestedClass_rendersInBody() {
            assertThat(format("class Outer { class Inner {} }"))
                .isEqualTo("class Outer {\n    class Inner {}\n}");
        }

        @Test
        void class_withNestedInterface_rendersInBody() {
            assertThat(format("class Outer { interface Inner {} }"))
                .isEqualTo("class Outer {\n    interface Inner {}\n}");
        }

        @Test
        void class_withNestedRecord_rendersInBody() {
            assertThat(format("class Outer { record Inner(int x) {} }"))
                .isEqualTo("class Outer {\n    record Inner(int x) {}\n}");
        }

        @Test
        void class_withNestedEnum_rendersInBody() {
            assertThat(format("class Outer { enum Inner { A } }"))
                .isEqualTo("class Outer {\n    enum Inner { A }\n}");
        }

        @Test
        void interface_withNestedClass_rendersInBody() {
            assertThat(format("interface Outer { class Inner {} }"))
                .isEqualTo("interface Outer {\n    class Inner {}\n}");
        }

        @Test
        void class_withMethodAndNestedClass_declarationOrderPreserved() {
            assertThat(format("class Outer { void a() {} class Inner {} }"))
                .isEqualTo("class Outer {\n    void a() {}\n\n    class Inner {}\n}");
        }

        @Test
        void class_withNestedClassThenMethod_reorderedSortsMethodFirst() {
            final var source = "class Outer { class Inner {} void a() {} }";
            assertThat(format(source, new GrindConfig(true)))
                .isEqualTo("class Outer {\n    void a() {}\n\n    class Inner {}\n}");
        }

        @Test
        void class_withFieldAndNestedClass_bothRender() {
            assertThat(format("class Outer { int x; class Inner {} }"))
                .isEqualTo("class Outer {\n    int x;\n\n    class Inner {}\n}");
        }

        @Test
        void class_withDeeplyNestedTypes_rendersAllLevels() {
            assertThat(format("class A { class B { class C {} } }"))
                .isEqualTo("class A {\n    class B {\n        class C {}\n    }\n}");
        }

        @Test
        void class_withNestedClassThatHasExtends_rendersFullHeader() {
            assertThat(format("class Outer { class Inner extends Base implements Runnable {} }"))
                .isEqualTo("class Outer {\n    class Inner extends Base implements Runnable {}\n}");
        }
    }
}
