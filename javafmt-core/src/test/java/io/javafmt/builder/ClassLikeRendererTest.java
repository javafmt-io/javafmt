package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.JavafmtConfig;
import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;

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
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source)));
    }

    private static String format(final String source, final JavafmtConfig config) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source), config));
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
            assertThat(format(source, new JavafmtConfig(true)))
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

    @Nested
    class InitBlocks {

        @Test
        void class_withStaticInitBlock_rendersWithStaticKeyword() {
            assertThat(format("class Foo { static { x = 1; } }"))
                .isEqualTo("class Foo {\n    static {\n        x = 1;\n    }\n}");
        }

        @Test
        void class_withEmptyStaticInitBlock_rendersOnOneLine() {
            assertThat(format("class Foo { static {} }"))
                .isEqualTo("class Foo {\n    static {}\n}");
        }

        @Test
        void class_withInstanceInitBlock_rendersWithoutKeyword() {
            assertThat(format("class Foo { { x = 1; } }"))
                .isEqualTo("class Foo {\n    {\n        x = 1;\n    }\n}");
        }

        @Test
        void class_withEmptyInstanceInitBlock_rendersOnOneLine() {
            assertThat(format("class Foo { {} }"))
                .isEqualTo("class Foo {\n    {}\n}");
        }

        @Test
        void class_withMultiStatementStaticInit_eachStmtOnOwnLine() {
            assertThat(format("class Foo { static { a(); b(); c(); } }"))
                .isEqualTo("class Foo {\n    static {\n        a();\n        b();\n        c();\n    }\n}");
        }

        @Test
        void class_withStaticInitAndStaticField_staticFieldFirst() {
            final var source = "class Foo { static { init(); } static int x = 1; }";
            assertThat(format(source, new JavafmtConfig(true)))
                .isEqualTo("class Foo {\n    static int x = 1;\n\n    static {\n        init();\n    }\n}");
        }

        @Test
        void class_withInstanceInitBetweenFieldAndConstructor_ordersCorrectly() {
            final var source = "class Foo { Foo() {} { init(); } int x; }";
            assertThat(format(source, new JavafmtConfig(true)))
                .isEqualTo(
                    "class Foo {\n    int x;\n\n    {\n        init();\n    }\n\n    Foo() {}\n}");
        }

        @Test
        void class_withBothInitBlocks_ordersStaticFirst() {
            final var source = "class Foo { { a(); } static { b(); } }";
            assertThat(format(source, new JavafmtConfig(true)))
                .isEqualTo(
                    "class Foo {\n    static {\n        b();\n    }\n\n    {\n        a();\n    }\n}");
        }

        @Test
        void class_withMethodAndInitBlocks_correctInterleaving() {
            final var source = "class Foo { class Inner {} void m() {} Foo() {} { ib(); } int ix; static { sb(); } static int sx; }";
            final var expected = """
                class Foo {
                    static int sx;

                    static {
                        sb();
                    }

                    int ix;

                    {
                        ib();
                    }

                    Foo() {}

                    void m() {}

                    class Inner {}
                }""";
            assertThat(format(source, new JavafmtConfig(true))).isEqualTo(expected);
        }

        @Test
        void record_withStaticInitBlock_rendersInBody() {
            assertThat(format("record R(int x) { static { init(); } }"))
                .isEqualTo("record R(int x) {\n    static {\n        init();\n    }\n}");
        }

        @Test
        void enum_withStaticInitBlock_rendersAfterConstants() {
            assertThat(format("enum E { A; static { init(); } }"))
                .isEqualTo("enum E {\n    A;\n\n    static {\n        init();\n    }\n}");
        }

        @Test
        void enum_withInstanceInitBlock_rendersInBody() {
            assertThat(format("enum E { A; { init(); } }"))
                .isEqualTo("enum E {\n    A;\n\n    {\n        init();\n    }\n}");
        }

        @Test
        void class_withDeeplyNestedStaticInit_rendersCorrectIndent() {
            final var source = "class Outer { class Inner { static { init(); } } }";
            final var expected = """
                class Outer {
                    class Inner {
                        static {
                            init();
                        }
                    }
                }""";
            assertThat(format(source)).isEqualTo(expected);
        }
    }

    @Nested
    class SealedNestedTypes {

        @Test
        void sealedInterface_withNestedPermittedSubtypes_rendersNestedTypesFirst() {
            final var source = "sealed interface Shape permits Circle, Square { int dim();"
                + " record Circle(int r) implements Shape {} record Square(int s) implements Shape {} }";
            final var expected = """
                sealed interface Shape permits Circle, Square {
                    record Circle(int r) implements Shape {}

                    record Square(int s) implements Shape {}

                    int dim();
                }""";
            assertThat(format(source, new JavafmtConfig(true))).isEqualTo(expected);
        }

        @Test
        void sealedClass_withNestedTypesAndFields_nestedTypesOnTop() {
            final var source = "sealed class Base permits Sub { void m() {} static int x; class Sub extends Base {} }";
            final var expected = """
                sealed class Base permits Sub {
                    class Sub extends Base {}

                    static int x;

                    void m() {}
                }""";
            assertThat(format(source, new JavafmtConfig(true))).isEqualTo(expected);
        }

        @Test
        void nonSealedClass_nestedTypesStayAtBottom() {
            final var source = "class Foo { class Inner {} int x; }";
            assertThat(format(source, new JavafmtConfig(true)))
                .isEqualTo("class Foo {\n    int x;\n\n    class Inner {}\n}");
        }

        @Test
        void sealedInterface_withMultipleNestedTypes_declarationOrderPreservedAmongThem() {
            final var source = "sealed interface Shape permits Zeta, Alpha {"
                + " int dim();"
                + " record Zeta(int z) implements Shape {}"
                + " record Alpha(int a) implements Shape {} }";
            final var expected = """
                sealed interface Shape permits Zeta, Alpha {
                    record Zeta(int z) implements Shape {}

                    record Alpha(int a) implements Shape {}

                    int dim();
                }""";
            assertThat(format(source, new JavafmtConfig(true))).isEqualTo(expected);
        }
    }
}
