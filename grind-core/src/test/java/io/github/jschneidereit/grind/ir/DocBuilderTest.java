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

class DocBuilderTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return Printer.print(DocBuilder.build(JavaParser.parse(source)), WIDTH);
    }

    @Nested
    class EmptyClassTests {

        @ParameterizedTest
        @ValueSource(strings = {"class Foo {}", "class Foo {   }", "class Foo {\n\n}", "class Foo {\n    \n}"})
        void emptyClass_noPackage_rendersOnOneLine(final String source) {
            assertThat(format(source)).isEqualTo("class Foo {}");
        }

        @ParameterizedTest
        @MethodSource("packageVariants")
        void packageDeclaration_normalizesToSingleNewlineThenClass(
                final String pkg, final String expected) {
            // extra blank lines between package and class should be collapsed to one newline
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
    class ClassWithFieldTests {

        @Test
        void singleField_isIndentedInsideClass() {
            assertThat(format("class Foo { int x; }"))
                .isEqualTo("class Foo {\n    int x;\n}");
        }
    }

    @Nested
    class ClassWithMethodTests {

        @Test
        void singleNoArgMethod_isIndentedInsideClass() {
            assertThat(format("class Foo { void bar() {} }"))
                .isEqualTo("class Foo {\n    void bar() {}\n}");
        }
    }

    @Nested
    class RecordTests {

        @Test
        void emptyRecord_rendersOnOneLine() {
            assertThat(format("record Empty() {}")).isEqualTo("record Empty() {}");
        }

        @Test
        void recordWithComponents_fitsOnOneLine() {
            assertThat(format("record Pair(int x, int y) {}")).isEqualTo("record Pair(int x, int y) {}");
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

    @Nested
    class MultipleMembersTests {

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
