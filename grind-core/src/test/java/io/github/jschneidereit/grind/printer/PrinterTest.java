package io.github.jschneidereit.grind.printer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.jschneidereit.grind.doc.Doc;

class PrinterTest {

    @Nested
    class TextTests {

        @Test
        void text_emitsValue() {
            assertThat(new Printer(80).print(new Doc.Text("hello"))).isEqualTo("hello");
        }

        @Test
        void text_empty_emitsNothing() {
            assertThat(new Printer(80).print(new Doc.Text(""))).isEmpty();
        }
    }

    @Nested
    class ConcatTests {

        @Test
        void concat_empty_emitsNothing() {
            assertThat(new Printer(80).print(new Doc.Concat(List.of()))).isEmpty();
        }

        @Test
        void concat_ofTexts_concatenates() {
            final var doc = new Doc.Concat(List.of(new Doc.Text("ab"), new Doc.Text("cd")));
            assertThat(new Printer(80).print(doc)).isEqualTo("abcd");
        }
    }

    @Nested
    class HardLineTests {

        @Test
        void hardLine_emitsNewline() {
            assertThat(new Printer(80).print(new Doc.HardLine())).isEqualTo("\n");
        }

        @Test
        void hardLine_withIndent_emitsNewlinePlusSpaces() {
            final var doc = new Doc.Indent(new Doc.Concat(List.of(
                new Doc.HardLine(),
                new Doc.Text("x"))));
            assertThat(new Printer(80).print(doc)).isEqualTo("\n    x");
        }

        @Test
        void hardLine_insideGroup_forcesBreak() {
            final var doc = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("a"),
                new Doc.HardLine(),
                new Doc.Text("b"))));
            assertThat(new Printer(100).print(doc)).isEqualTo("a\nb");
        }
    }

    @Nested
    class LineTests {

        @Test
        void line_atRoot_emitsNewline() {
            assertThat(new Printer(80).print(new Doc.Line())).isEqualTo("\n");
        }

        @ParameterizedTest
        @MethodSource("lineFitCases")
        void group_line_fitsOrBreaks(final int lineWidth, final String expected) {
            final var doc = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("hello"),
                new Doc.Line(),
                new Doc.Text("world"))));
            assertThat(new Printer(lineWidth).print(doc)).isEqualTo(expected);
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
            assertThat(new Printer(80).print(new Doc.SoftLine())).isEqualTo("\n");
        }

        @ParameterizedTest
        @MethodSource("softLineFitCases")
        void group_softLine_fitsOrBreaks(final int lineWidth, final String expected) {
            final var doc = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("ab"),
                new Doc.SoftLine(),
                new Doc.Text("cd"))));
            assertThat(new Printer(lineWidth).print(doc)).isEqualTo(expected);
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
            assertThat(new Printer(80).print(doc)).isEqualTo("\n    x");
        }

        @Test
        void nestedIndent_addsFourSpacesEachLevel() {
            final var doc = new Doc.Indent(new Doc.Indent(new Doc.Concat(List.of(
                new Doc.HardLine(),
                new Doc.Text("x")))));
            assertThat(new Printer(80).print(doc)).isEqualTo("\n        x");
        }
    }

    @Nested
    class IfBreakTests {

        @Test
        void ifBreak_atRoot_usesBreakContents() {
            final var doc = new Doc.IfBreak(new Doc.Text("B"), new Doc.Text("F"));
            assertThat(new Printer(80).print(doc)).isEqualTo("B");
        }

        @Test
        void ifBreak_insideFittingGroup_usesFlatContents() {
            final var doc = new Doc.Group(new Doc.IfBreak(new Doc.Text("B"), new Doc.Text("F")));
            assertThat(new Printer(80).print(doc)).isEqualTo("F");
        }
    }

    @Nested
    class FillTests {

        private static Doc fill(final String... words) {
            final var parts = new java.util.ArrayList<Doc>();
            for (var i = 0; i < words.length; i++) {
                if (i > 0) {
                    parts.add(new Doc.Line());
                }
                parts.add(new Doc.Text(words[i]));
            }
            return new Doc.Fill(parts);
        }

        @Test
        void fill_allFitOnOneLine_keepsOnOneLine() {
            assertThat(new Printer(80).print(fill("a", "b", "c"))).isEqualTo("a b c");
        }

        @Test
        void fill_packsAsManyAsFitPerLine() {
            // "aaa bbb ccc ddd" = 15. At width 8: "aaa bbb" (7) fits, "ccc" doesn't fit with " ccc" (11>8), break;
            //                                     "ccc ddd" (7) fits on second line.
            assertThat(new Printer(8).print(fill("aaa", "bbb", "ccc", "ddd")))
                .isEqualTo("aaa bbb\nccc ddd");
        }

        @Test
        void fill_breaksBeforeItemThatWontFit() {
            // Width 5: "aaa" (3) fits, " bb" (6) doesn't, break; "bb cc" (5) fits.
            assertThat(new Printer(5).print(fill("aaa", "bb", "cc")))
                .isEqualTo("aaa\nbb cc");
        }

        @Test
        void fill_decidesEachBreakIndependently() {
            // Contrast with Group: Group is all-or-nothing.
            final var grp = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("aaa"), new Doc.Line(),
                new Doc.Text("bb"), new Doc.Line(),
                new Doc.Text("cc"))));
            // Group at width 5: "aaa bb cc" = 9 > 5 so all break.
            assertThat(new Printer(5).print(grp)).isEqualTo("aaa\nbb\ncc");
            // Fill at width 5: packs partial lines.
            assertThat(new Printer(5).print(fill("aaa", "bb", "cc"))).isEqualTo("aaa\nbb cc");
        }

        @Test
        void fill_softLineSeparatorPacksWithoutSpace() {
            final var doc = new Doc.Fill(List.of(
                new Doc.Text("aa"), new Doc.SoftLine(),
                new Doc.Text("bb"), new Doc.SoftLine(),
                new Doc.Text("cc")));
            assertThat(new Printer(80).print(doc)).isEqualTo("aabbcc");
            assertThat(new Printer(3).print(doc)).isEqualTo("aa\nbb\ncc");
        }

        @Test
        void fill_requiresOddNumberOfParts() {
            assertThatThrownBy(() -> new Doc.Fill(List.of(new Doc.Text("a"), new Doc.Line())))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void fill_requiresLineOrSoftLineSeparator() {
            assertThatThrownBy(() -> new Doc.Fill(List.of(
                new Doc.Text("a"), new Doc.Text(","), new Doc.Text("b"))))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void fill_hardLineInsideContent_forcesNextSeparatorToBreak() {
            // A HardLine inside a Fill content sets a force-break flag in the pack loop:
            // the immediately following separator BREAKs regardless of the rendered cursor's
            // actual column after the HardLine. After that break the flag clears and packing
            // resumes normally. Documenting this so a future change doesn't quietly alter
            // the behavior.
            final var contentWithHardLine = new Doc.Concat(List.of(
                new Doc.Text("a"), new Doc.HardLine(), new Doc.Text("b")));
            final var doc = new Doc.Fill(List.of(
                contentWithHardLine, new Doc.Line(),
                new Doc.Text("c"), new Doc.Line(),
                new Doc.Text("d")));

            assertThat(new Printer(80).print(doc)).isEqualTo("a\nb\nc d");
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void group_whenColAlreadyExceedsLineWidth_alwaysBreaks() {
            // col=6 after Text("123456"), lineWidth=5 → remaining=-1, group always breaks
            final var doc = new Doc.Concat(List.of(
                new Doc.Text("123456"),
                new Doc.Group(new Doc.Concat(List.of(
                    new Doc.Text("a"),
                    new Doc.Line(),
                    new Doc.Text("b"))))));
            assertThat(new Printer(5).print(doc)).isEqualTo("123456a\nb");
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
            assertThat(new Printer(14).print(blockDoc())).isEqualTo("{ x: 1, y: 2 }");
        }

        @Test
        void groupWithIndentedLines_breaksBeyondWidth() {
            assertThat(new Printer(13).print(blockDoc())).isEqualTo("{\n    x: 1,\n    y: 2\n}");
        }
    }

    @Nested
    class ConstructorValidationTests {

        @Test
        void lineWidth_zero_throws() {
            assertThatThrownBy(() -> new Printer(0, PrintStrategy.greedy()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void lineWidth_negative_throws() {
            assertThatThrownBy(() -> new Printer(-1, PrintStrategy.greedy()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void lineWidth_one_isValid() {
            assertThat(new Printer(1, PrintStrategy.greedy()).print(new Doc.Text("x"))).isEqualTo("x");
        }
    }

    @Nested
    class ScalingTests {

        private static Doc nestedGroups(final int n) {
            var inner = (Doc) new Doc.Text("x");
            for (var i = 0; i < n; i++) {
                inner = new Doc.Group(new Doc.Concat(List.of(
                    new Doc.Text("g"),
                    new Doc.Line(),
                    inner)));
            }
            return inner;
        }

        @Test
        @Timeout(value = 2, unit = TimeUnit.SECONDS)
        void deeplyNestedGroupDecisions_runInLinearTime() {
            // Each nested Group must make its own fit decision (mode=BREAK propagates inward
            // because lineWidth=1 forces every Group to break). A naive flat-width walker
            // re-traverses the entire remaining subtree per Group → O(n²); a short-circuiting
            // fits(remaining) predicate stops at the first overflow → O(n) total. The bound
            // here is generous (well under a second under O(n), many seconds under O(n²)) so
            // it fails loudly if a future change reintroduces full-subtree walks for fit checks.
            final var n = 50_000;
            final var rendered = new Printer(1).print(nestedGroups(n));
            assertThat(rendered).hasSize(2 * n + 1);
        }
    }
}
