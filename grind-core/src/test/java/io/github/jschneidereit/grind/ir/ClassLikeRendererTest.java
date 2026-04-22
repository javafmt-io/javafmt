package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

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
}
