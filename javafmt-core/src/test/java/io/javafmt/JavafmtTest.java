package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JavafmtTest {

    @Test
    void formatEmptySourceReturnsEmptyString() {
        assertThat(Javafmt.format("")).isEqualTo("");
    }

    @Test
    void formatNormalisesWhitespace() {
        assertThat(Javafmt.format("class Foo { int x; }"))
            .isEqualTo("class Foo {\n    int x;\n}");
    }

    @Test
    void formatIsIdempotent() {
        final var source = "class Foo { int x; }";
        assertThat(Javafmt.format(Javafmt.format(source))).isEqualTo(Javafmt.format(source));
    }

    @ParameterizedTest(name = "blank {1}")
    @MethodSource("blankInputs")
    void formatOnBlankInputIsIdempotent(final String input, final String label) {
        final var first = Javafmt.format(input);
        assertThat(Javafmt.format(first)).as("idempotent for %s", label).isEqualTo(first);
    }

    static Stream<Arguments> blankInputs() {
        return Stream.of(
            Arguments.of("", "empty"),
            Arguments.of(" ", "single space"),
            Arguments.of("\t", "single tab"),
            Arguments.of("\n\n\n", "blank lines"));
    }

    @Nested
    class EdgeCasesTest {

        @Test
        void identifierLongerThan150Chars_isNotBroken() {
            final var longName = "a".repeat(151);
            final var source = "class C { int " + longName + " = 0; }";
            final var formatted = Javafmt.format(source);
            assertThat(Arrays.stream(formatted.split("\n"))
                .max(Comparator.comparingInt(String::length)))
                .hasValueSatisfying(longest -> assertThat(longest.length()).isGreaterThan(150));
            assertThat(formatted).contains(longName);
        }

        @Test
        void exactly150CharLine_isPreservedWithoutWrapping() {
            // "    int " (8) + name (137) + " = 1;" (5) = 150 chars exactly
            final var name = "a".repeat(137);
            final var source = "class C {\n    int " + name + " = 1;\n}";
            final var formatted = Javafmt.format(source);
            assertThat(Arrays.stream(formatted.split("\n"))
                .filter(l -> l.contains(name))
                .findFirst())
                .hasValueSatisfying(line -> assertThat(line.length()).isEqualTo(150));
        }

        @Test
        void crlfInput_producesLfOnlyOutput() {
            final var source = "class C {\r\n    int x;\r\n}";
            final var formatted = Javafmt.format(source);
            assertThat(formatted).doesNotContain("\r");
            assertThat(formatted).isEqualTo("class C {\n    int x;\n}");
        }

        @Test
        void tabIndentedInput_normalizesToFourSpaces() {
            final var formatted = Javafmt.format("class C {\n\tint x;\n}");
            assertThat(formatted).isEqualTo("class C {\n    int x;\n}");
        }

        @Test
        void fileWithOnlyPackage_isFormattedIdempotently() {
            final var source = "package com.example;";
            final var first = Javafmt.format(source);
            assertThat(Javafmt.format(first)).isEqualTo(first);
        }

        @Test
        void fileWithOnlyImports_isHandledGracefully() {
            final var source = "import java.util.List;";
            final var first = Javafmt.format(source);
            assertThat(Javafmt.format(first)).isEqualTo(first);
        }
    }

    @Nested
    class NullChecksTest {

        @Test
        void format_nullSource_throws() {
            assertThatThrownBy(() -> Javafmt.format(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void format_nullConfig_throws() {
            assertThatThrownBy(() -> Javafmt.format("class A {}", null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void formatWithResult_nullSource_throws() {
            assertThatThrownBy(() -> Javafmt.formatWithResult(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void formatWithResult_sourceAndConfig_nullSource_throws() {
            assertThatThrownBy(() -> Javafmt.formatWithResult(null, JavafmtConfig.defaults()))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void formatWithResult_sourceAndConfig_nullConfig_throws() {
            assertThatThrownBy(() -> Javafmt.formatWithResult("class A {}", (JavafmtConfig) null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void formatWithResult_sourceAndOutcome_nullSource_throws() {
            assertThatThrownBy(() -> Javafmt.formatWithResult(null, (io.javafmt.parser.ParseOutcome) null))
                .isInstanceOf(NullPointerException.class);
        }
    }
}
