package io.github.jschneidereit.grind.printer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.jschneidereit.grind.ir.Doc;

class PrinterTest {

    @Nested
    class TextTests {

        @Test
        void text_emitsValue() {
            assertThat(Printer.print(new Doc.Text("hello"), 80)).isEqualTo("hello");
        }

        @Test
        void text_empty_emitsNothing() {
            assertThat(Printer.print(new Doc.Text(""), 80)).isEmpty();
        }
    }

    @Nested
    class ConcatTests {

        @Test
        void concat_empty_emitsNothing() {
            assertThat(Printer.print(new Doc.Concat(List.of()), 80)).isEmpty();
        }

        @Test
        void concat_ofTexts_concatenates() {
            final var doc = new Doc.Concat(List.of(new Doc.Text("ab"), new Doc.Text("cd")));
            assertThat(Printer.print(doc, 80)).isEqualTo("abcd");
        }
    }

    @Nested
    class HardLineTests {

        @Test
        void hardLine_emitsNewline() {
            assertThat(Printer.print(new Doc.HardLine(), 80)).isEqualTo("\n");
        }

        @Test
        void hardLine_withIndent_emitsNewlinePlusSpaces() {
            final var doc = new Doc.Indent(new Doc.Concat(List.of(
                new Doc.HardLine(),
                new Doc.Text("x"))));
            assertThat(Printer.print(doc, 80)).isEqualTo("\n    x");
        }

        @Test
        void hardLine_insideGroup_forcesBreak() {
            final var doc = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("a"),
                new Doc.HardLine(),
                new Doc.Text("b"))));
            assertThat(Printer.print(doc, 100)).isEqualTo("a\nb");
        }
    }

    @Nested
    class LineTests {

        @Test
        void line_atRoot_emitsNewline() {
            assertThat(Printer.print(new Doc.Line(), 80)).isEqualTo("\n");
        }

        @ParameterizedTest
        @MethodSource("lineFitCases")
        void group_line_fitsOrBreaks(final int lineWidth, final String expected) {
            final var doc = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("hello"),
                new Doc.Line(),
                new Doc.Text("world"))));
            assertThat(Printer.print(doc, lineWidth)).isEqualTo(expected);
        }

        static Stream<Arguments> lineFitCases() {
            return Stream.of(
                Arguments.of(11, "hello world"),
                Arguments.of(12, "hello world"),
                Arguments.of(10, "hello\nworld"),
                Arguments.of(5, "hello\nworld"));
        }
    }

    @Nested
    class SoftLineTests {

        @Test
        void softLine_atRoot_emitsNewline() {
            assertThat(Printer.print(new Doc.SoftLine(), 80)).isEqualTo("\n");
        }

        @ParameterizedTest
        @MethodSource("softLineFitCases")
        void group_softLine_fitsOrBreaks(final int lineWidth, final String expected) {
            final var doc = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("ab"),
                new Doc.SoftLine(),
                new Doc.Text("cd"))));
            assertThat(Printer.print(doc, lineWidth)).isEqualTo(expected);
        }

        static Stream<Arguments> softLineFitCases() {
            return Stream.of(
                Arguments.of(4, "abcd"),
                Arguments.of(5, "abcd"),
                Arguments.of(3, "ab\ncd"));
        }
    }

    @Nested
    class IndentTests {

        @Test
        void indent_addsSpacesToNestedContent() {
            final var doc = new Doc.Indent(new Doc.Concat(List.of(
                new Doc.HardLine(),
                new Doc.Text("x"))));
            assertThat(Printer.print(doc, 80)).isEqualTo("\n    x");
        }

        @Test
        void nestedIndent_addsFourSpacesEachLevel() {
            final var doc = new Doc.Indent(new Doc.Indent(new Doc.Concat(List.of(
                new Doc.HardLine(),
                new Doc.Text("x")))));
            assertThat(Printer.print(doc, 80)).isEqualTo("\n        x");
        }
    }

    @Nested
    class IfBreakTests {

        @Test
        void ifBreak_atRoot_usesBreakContents() {
            final var doc = new Doc.IfBreak(new Doc.Text("B"), new Doc.Text("F"));
            assertThat(Printer.print(doc, 80)).isEqualTo("B");
        }

        @Test
        void ifBreak_insideFittingGroup_usesFlatContents() {
            final var doc = new Doc.Group(new Doc.IfBreak(new Doc.Text("B"), new Doc.Text("F")));
            assertThat(Printer.print(doc, 80)).isEqualTo("F");
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void group_whenColAlreadyExceedsLineWidth_alwaysBreaks() {
            // col=6 after Text("123456"), lineWidth=5 → fits(-1, …) → false immediately
            final var doc = new Doc.Concat(List.of(
                new Doc.Text("123456"),
                new Doc.Group(new Doc.Concat(List.of(
                    new Doc.Text("a"),
                    new Doc.Line(),
                    new Doc.Text("b"))))));
            assertThat(Printer.print(doc, 5)).isEqualTo("123456a\nb");
        }
    }

    @Nested
    class ComplexTests {

        private static Doc blockDoc() {
            return new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("{"),
                new Doc.Indent(new Doc.Concat(List.of(
                    new Doc.Line(),
                    new Doc.Text("x: 1,"),
                    new Doc.Line(),
                    new Doc.Text("y: 2")))),
                new Doc.Line(),
                new Doc.Text("}"))));
        }

        @Test
        void groupWithIndentedLines_fitsFlat() {
            // flat rendering: "{ x: 1, y: 2 }" = 14 chars
            assertThat(Printer.print(blockDoc(), 14)).isEqualTo("{ x: 1, y: 2 }");
        }

        @Test
        void groupWithIndentedLines_breaksBeyondWidth() {
            assertThat(Printer.print(blockDoc(), 13)).isEqualTo("{\n    x: 1,\n    y: 2\n}");
        }
    }
}
