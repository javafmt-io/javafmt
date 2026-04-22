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

    @Nested
    class ClassWithMethodTests {

        @Test
        void singleNoArgMethod_isIndentedInsideClass() {
            assertThat(format("class Foo { void bar() {} }"))
                .isEqualTo("class Foo {\n    void bar() {}\n}");
        }

        @Test
        void methodWithSingleParamterlessAnnotation_rendersInline() {
            assertThat(format("class Foo { @Override void bar() {} }"))
                .isEqualTo("class Foo {\n    @Override void bar() {}\n}");
        }

        @Test
        void methodWithParameterizedAnnotation_rendersOnOwnLine() {
            assertThat(format("class Foo { @SuppressWarnings(\"all\") void bar() {} }"))
                .isEqualTo("class Foo {\n    @SuppressWarnings(\"all\")\n    void bar() {}\n}");
        }

        @Test
        void methodWithMultipleAnnotations_eachOnOwnLine() {
            assertThat(format("class Foo { @Deprecated @Override void bar() {} }"))
                .isEqualTo("class Foo {\n    @Deprecated\n    @Override\n    void bar() {}\n}");
        }

        @Test
        void methodWithNamedArgAnnotation_rendersOnOwnLine() {
            assertThat(format("class Foo { @Getter(lazy = true) int bar() { return 42; } }"))
                .isEqualTo("class Foo {\n    @Getter(lazy = true)\n    int bar() {\n        return 42;\n    }\n}");
        }

        @Test
        void methodWithReturnStatement_rendersBody() {
            assertThat(format("class Foo { int bar() { return 42; } }"))
                .isEqualTo("class Foo {\n    int bar() {\n        return 42;\n    }\n}");
        }

        @Test
        void methodWithExpressionStatement_rendersBody() {
            assertThat(format("class Foo { void bar() { System.out.println(42); } }"))
                .isEqualTo("class Foo {\n    void bar() {\n        System.out.println(42);\n    }\n}");
        }

        @Test
        void methodWithLocalVariable_rendersBody() {
            assertThat(format("class Foo { int bar() { int x = 1; return x; } }"))
                .isEqualTo("class Foo {\n    int bar() {\n        int x = 1;\n        return x;\n    }\n}");
        }
    }

    @Nested
    class ClassAnnotationTests {

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
    class ImportTests {

        @Test
        void unsortedNonStaticImports_areSorted() {
            assertThat(format("""
                    import java.util.List;
                    import java.io.File;
                    class Foo {}"""))
                .isEqualTo("import java.io.File;\nimport java.util.List;\n\nclass Foo {}");
        }

        @Test
        void mixedImports_staticFirstThenBlankLineThenNonStatic() {
            assertThat(format("""
                    import java.util.List;
                    import static java.util.Collections.emptyList;
                    class Foo {}"""))
                .isEqualTo("import static java.util.Collections.emptyList;\n\nimport java.util.List;\n\nclass Foo {}");
        }

        @Test
        void alreadySortedImports_areIdempotent() {
            final var source = "import java.io.File;\nimport java.util.List;\nclass Foo {}";
            assertThat(format(format(source))).isEqualTo(format(source));
        }

        @Test
        void importsAfterPackage_haveBlankLineSeparator() {
            assertThat(format("""
                    package com.example;
                    import java.util.List;
                    class Foo {}"""))
                .isEqualTo("package com.example;\n\nimport java.util.List;\n\nclass Foo {}");
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
