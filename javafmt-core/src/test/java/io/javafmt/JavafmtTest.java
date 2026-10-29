package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JavafmtTest {

    @Test
    void formatEmptySourceReturnsEmptyString() {
        assertThat(Javafmt.format("").stripTrailing().stripTrailing()).isEqualTo("");
    }

    @Test
    void formatNormalisesWhitespace() {
        assertThat(Javafmt.format("class Foo { int x; }").stripTrailing().stripTrailing()).isEqualTo("class Foo {\n\n    int x;\n}");
    }

    @Test
    void formatIsIdempotent() {
        final var source = "class Foo { int x; }";
        assertThat(Javafmt.format(Javafmt.format(source)).stripTrailing().stripTrailing()).isEqualTo(Javafmt.format(source).stripTrailing());
    }

    @ParameterizedTest(name = "blank {1}")
    @MethodSource("blankInputs")
    void formatOnBlankInputIsIdempotent(final String input, final String label) {
        final var first = Javafmt.format(input).stripTrailing();
        assertThat(Javafmt.format(first).stripTrailing().stripTrailing())
                .as("idempotent for %s", label)
                .isEqualTo(first);
    }

    static Stream<Arguments> blankInputs() {
        return Stream.of(
                Arguments.of("", "empty"),
                Arguments.of(" ", "single space"),
                Arguments.of("\t", "single tab"),
                Arguments.of("\n\n\n", "blank lines"));
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void deeplyNestedCodeFormatsInProportionToItsSize() {
        // Generated code nests this deeply. Each level indents another 8 columns, so the output is
        // hundreds of lines thousands of columns wide, and the trailing-whitespace pass used to be
        // quadratic in line length: this took 4 seconds, and the curve was steeper than the input.
        var nested = "a";
        for (var level = 0; level < 600; level++) {
            nested = "f" + level + "(" + nested + ")";
        }
        final var source = "class Deep {\n\n    Object v(final Object a) {\n        return " + nested + ";\n    }\n}\n";
        assertThat(Javafmt.formatWithResult(source).diagnostics()).isEmpty();
    }

    @Nested
    class EdgeCasesTest {

        @Test
        void identifierLongerThan150Chars_isNotBroken() {
            final var longName = "a".repeat(151);
            final var source = "class C { int " + longName + " = 0; }";
            final var formatted = Javafmt.format(source).stripTrailing();
            assertThat(Arrays.stream(formatted.split("\n")).max(Comparator.comparingInt(String::length)))
                    .hasValueSatisfying(longest -> assertThat(longest.length()).isGreaterThan(150));
            assertThat(formatted).contains(longName);
        }

        @Test
        void exactly150CharLine_isPreservedWithoutWrapping() {
            // "    int " (8) + name (137) + " = 1;" (5) = 150 chars exactly
            final var name = "a".repeat(137);
            final var source = "class C {\n\n    int " + name + " = 1;\n}";
            final var formatted = Javafmt.format(source).stripTrailing();
            assertThat(Arrays.stream(formatted.split("\n")).filter(l -> l.contains(name)).findFirst())
                    .hasValueSatisfying(line -> assertThat(line.length()).isEqualTo(150));
        }

        @Test
        void crlfInputIsWrittenBackAsCrlf() {
            // Rewriting a CRLF file to LF makes every line of it a diff, and whatever javafmt
            // actually changed disappears inside that.
            final var source = "class C {\r\n    int x;\r\n}\r\n";
            assertThat(Javafmt.format(source)).isEqualTo("class C {\r\n\r\n    int x;\r\n}\r\n");
        }

        @Test
        void crlfOutputIsIdempotent() {
            final var formatted = Javafmt.format("class C {\r\n    int x;\r\n}\r\n");
            assertThat(Javafmt.format(formatted)).isEqualTo(formatted);
        }

        @Test
        void aByteOrderMarkSurvives() {
            final var formatted = Javafmt.format("\uFEFFclass C {\n\n    int x;\n}\n");
            assertThat(formatted).isEqualTo("\uFEFFclass C {\n\n    int x;\n}\n");
        }

        @Test
        void mixedEndingsNormaliseToTheDominantOne() {
            // Three CRLF endings against one LF: the file has to be given one of them, and the
            // majority is the smaller diff.
            final var source = "class C {\r\n    int x;\r\n    int y;\n    int z;\r\n}\r\n";
            assertThat(Javafmt.format(source).replace("\r\n", "")).doesNotContain("\n");
        }

        @Test
        void aLoneCarriageReturnCountsAsALineEnding() {
            assertThat(Javafmt.format("class C {\r    int x;\r}\r")).isEqualTo("class C {\r\r    int x;\r}\r");
        }

        @Test
        void theBatchPathKeepsEachFilesOwnEndings() {
            final var crlf = "class A {\r\n    int x;\r\n}\r\n";
            final var lf = "class B {\n\n    int y;\n}\n";
            final var results = Javafmt.formatAll(java.util.List.of(crlf, lf), JavafmtConfig.defaults(), 2);
            assertThat(results.getFirst().output())
                    .isEqualTo(Javafmt.format(crlf))
                    .contains("\r\n");
            assertThat(results.get(1).output()).isEqualTo(lf).doesNotContain("\r");
        }

        @Test
        void tabIndentedInput_normalizesToFourSpaces() {
            final var formatted = Javafmt.format("class C {\n\n\tint x;\n}").stripTrailing();
            assertThat(formatted).isEqualTo("class C {\n\n    int x;\n}");
        }

        @Test
        void fileWithOnlyPackage_isFormattedIdempotently() {
            final var source = "package com.example;";
            final var first = Javafmt.format(source).stripTrailing();
            assertThat(Javafmt.format(first).stripTrailing().stripTrailing()).isEqualTo(first);
        }

        @Test
        void fileWithOnlyImports_isHandledGracefully() {
            final var source = "import java.util.List;";
            final var first = Javafmt.format(source).stripTrailing();
            assertThat(Javafmt.format(first).stripTrailing().stripTrailing()).isEqualTo(first);
        }
    }

    @Nested
    class NullChecksTest {

        @Test
        void format_nullSource_throws() {
            assertThatThrownBy(() -> Javafmt.format(null).stripTrailing()).isInstanceOf(NullPointerException.class);
        }

        @Test
        void format_nullConfig_throws() {
            assertThatThrownBy(() -> Javafmt.format("class A {}", null).stripTrailing()).isInstanceOf(NullPointerException.class);
        }

        @Test
        void formatWithResult_nullSource_throws() {
            assertThatThrownBy(() -> Javafmt.formatWithResult(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void formatWithResult_sourceAndConfig_nullSource_throws() {
            assertThatThrownBy(() -> Javafmt.formatWithResult(null, JavafmtConfig.defaults())).isInstanceOf(NullPointerException.class);
        }

        @Test
        void formatWithResult_sourceAndConfig_nullConfig_throws() {
            assertThatThrownBy(() -> Javafmt.formatWithResult("class A {}", (JavafmtConfig) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void formatWithResult_sourceAndOutcome_nullSource_throws() {
            assertThatThrownBy(() -> Javafmt.formatWithResult(null, (io.javafmt.parser.ParseOutcome) null)).isInstanceOf(NullPointerException.class);
        }
    }
}
