package io.github.jschneidereit.grind.printer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.jschneidereit.grind.ir.Doc;

class WadlerLindigPrintStrategyTest {

    private static Printer sut(final int lineWidth) {
        return new Printer(lineWidth, PrintStrategy.wadlerLindig());
    }

    @Nested
    class EquivalenceWithGreedy {

        @Test
        void text_emitsValue() {
            assertThat(sut(80).print(new Doc.Text("hello"))).isEqualTo("hello");
        }

        @Test
        void concat_ofTexts_concatenates() {
            final var doc = new Doc.Concat(List.of(new Doc.Text("ab"), new Doc.Text("cd")));
            assertThat(sut(80).print(doc)).isEqualTo("abcd");
        }

        @Test
        void hardLine_insideGroup_forcesBreak() {
            final var doc = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("a"),
                new Doc.HardLine(),
                new Doc.Text("b"))));
            assertThat(sut(100).print(doc)).isEqualTo("a\nb");
        }

        @ParameterizedTest
        @MethodSource("lineFitCases")
        void group_line_fitsOrBreaks(final int lineWidth, final String expected) {
            final var doc = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("hello"),
                new Doc.Line(),
                new Doc.Text("world"))));
            assertThat(sut(lineWidth).print(doc)).isEqualTo(expected);
        }

        static Stream<Arguments> lineFitCases() {
            return Stream.of(
                Arguments.of(11, "hello world"),
                Arguments.of(12, "hello world"),
                Arguments.of(10, "hello\nworld"),
                Arguments.of(5, "hello\nworld"));
        }

        @Test
        void softLine_insideGroup_fitsFlat() {
            final var doc = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("ab"),
                new Doc.SoftLine(),
                new Doc.Text("cd"))));
            assertThat(sut(4).print(doc)).isEqualTo("abcd");
        }

        @Test
        void softLine_insideGroup_breaksBeyondWidth() {
            final var doc = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("ab"),
                new Doc.SoftLine(),
                new Doc.Text("cd"))));
            assertThat(sut(3).print(doc)).isEqualTo("ab\ncd");
        }

        @Test
        void ifBreak_insideFittingGroup_usesFlatContents() {
            final var doc = new Doc.Group(new Doc.IfBreak(new Doc.Text("B"), new Doc.Text("F")));
            assertThat(sut(80).print(doc)).isEqualTo("F");
        }

        @Test
        void ifBreak_atRoot_usesBreakContents() {
            final var doc = new Doc.IfBreak(new Doc.Text("B"), new Doc.Text("F"));
            assertThat(sut(80).print(doc)).isEqualTo("B");
        }

        @Test
        void groupWithIndentedLines_fitsFlat() {
            assertThat(sut(14).print(blockDoc())).isEqualTo("{ x: 1, y: 2 }");
        }

        @Test
        void groupWithIndentedLines_breaksBeyondWidth() {
            assertThat(sut(13).print(blockDoc())).isEqualTo("{\n    x: 1,\n    y: 2\n}");
        }

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
    }

    @Nested
    class FillBehaviorMatchesGreedy {

        // Fill has no Wadler-Lindig analogue — WL keeps the existing greedy Fill semantics.

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
        void fill_packsAsManyAsFitPerLine() {
            assertThat(sut(8).print(fill("aaa", "bbb", "ccc", "ddd"))).isEqualTo("aaa bbb\nccc ddd");
        }

        @Test
        void fill_breaksBeforeItemThatWontFit() {
            assertThat(sut(5).print(fill("aaa", "bb", "cc"))).isEqualTo("aaa\nbb cc");
        }
    }

    @Nested
    class LookaheadAwareFitDecision {

        // The defining difference between Wadler-Lindig and the local-greedy printer:
        // when deciding whether a Group can render FLAT, WL considers what comes AFTER
        // the group on the same line. Local-greedy commits to FLAT based on the group's
        // own width and then lets the suffix overflow; WL sees the suffix would push the
        // line past lineWidth and breaks the group.

        @Test
        void groupBreaks_whenSuffixWouldOverflowFlatLine() {
            // Group's flat form is "aa bb" (5). With lineWidth=7 and no suffix the group fits.
            // But the suffix " cc" pushes total to 8 > 7. WL breaks the group; greedy would not.
            final var doc = new Doc.Concat(List.of(
                new Doc.Group(new Doc.Concat(List.of(
                    new Doc.Text("aa"), new Doc.Line(), new Doc.Text("bb")))),
                new Doc.Text(" cc")));
            assertThat(sut(7).print(doc)).isEqualTo("aa\nbb cc");
        }

        @Test
        void groupFlat_whenSuffixFitsTogether() {
            // Same shape, lineWidth=8: group flat "aa bb" + suffix " cc" = 8 ≤ 8, fits flat.
            final var doc = new Doc.Concat(List.of(
                new Doc.Group(new Doc.Concat(List.of(
                    new Doc.Text("aa"), new Doc.Line(), new Doc.Text("bb")))),
                new Doc.Text(" cc")));
            assertThat(sut(8).print(doc)).isEqualTo("aa bb cc");
        }

        @Test
        void scanStops_atForcedBreakInUpcomingFrames() {
            // After the candidate group there is a HardLine — the current line ends there,
            // so the suffix Text("xxx") doesn't count against the candidate's fit decision.
            final var doc = new Doc.Concat(List.of(
                new Doc.Group(new Doc.Concat(List.of(
                    new Doc.Text("aa"), new Doc.Line(), new Doc.Text("bb")))),
                new Doc.HardLine(),
                new Doc.Text("xxxxxxxxxx")));
            assertThat(sut(7).print(doc)).isEqualTo("aa bb\nxxxxxxxxxx");
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
            final var n = 50_000;
            final var rendered = sut(1).print(nestedGroups(n));
            assertThat(rendered).hasSize(2 * n + 1);
        }
    }
}
