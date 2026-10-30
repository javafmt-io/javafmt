package io.javafmt.printer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.javafmt.doc.Doc;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
            assertThat(new Printer(80).print(Doc.HARD_LINE)).isEqualTo("\n");
        }

        @Test
        void hardLine_withIndent_emitsNewlinePlusSpaces() {
            final var doc = new Doc.Indent(new Doc.Concat(List.of(Doc.HARD_LINE, new Doc.Text("x"))));
            assertThat(new Printer(80).print(doc)).isEqualTo("\n    x");
        }

        @Test
        void hardLine_insideGroup_forcesBreak() {
            final var doc = new Doc.Group(new Doc.Concat(List.of(new Doc.Text("a"), Doc.HARD_LINE, new Doc.Text("b"))));
            assertThat(new Printer(100).print(doc)).isEqualTo("a\nb");
        }
    }

    @Nested
    class LineTests {

        @Test
        void line_atRoot_emitsNewline() {
            assertThat(new Printer(80).print(Doc.LINE)).isEqualTo("\n");
        }

        @ParameterizedTest
        @MethodSource("lineFitCases")
        void group_line_fitsOrBreaks(final int lineWidth, final String expected) {
            final var doc = new Doc.Group(new Doc.Concat(List.of(new Doc.Text("hello"), Doc.LINE, new Doc.Text("world"))));
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
            assertThat(new Printer(80).print(Doc.SOFT_LINE)).isEqualTo("\n");
        }

        @ParameterizedTest
        @MethodSource("softLineFitCases")
        void group_softLine_fitsOrBreaks(final int lineWidth, final String expected) {
            final var doc = new Doc.Group(new Doc.Concat(List.of(new Doc.Text("ab"), Doc.SOFT_LINE, new Doc.Text("cd"))));
            assertThat(new Printer(lineWidth).print(doc)).isEqualTo(expected);
        }

        static Stream<Arguments> softLineFitCases() {
            return Stream.of(Arguments.of(4, "abcd"), Arguments.of(5, "abcd"), Arguments.of(3, "ab\ncd"));
        }
    }

    @Nested
    class IndentTests {

        @Test
        void indent_addsSpacesToNestedContent() {
            final var doc = new Doc.Indent(new Doc.Concat(List.of(Doc.HARD_LINE, new Doc.Text("x"))));
            assertThat(new Printer(80).print(doc)).isEqualTo("\n    x");
        }

        @Test
        void nestedIndent_addsFourSpacesEachLevel() {
            final var doc = new Doc.Indent(new Doc.Indent(new Doc.Concat(List.of(Doc.HARD_LINE, new Doc.Text("x")))));
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
    class BlockCommentTests {

        static Stream<Arguments> placements() {
            return Stream.of(
                    Arguments.of("x; ", 3, List.of("/* a", "      b */"), "x; /* a\n      b */"),
                    Arguments.of("xyz; ", 3, List.of("/* a", "      b */"), "xyz; /* a\n        b */"),
                    Arguments.of("x; ", 9, List.of("/* a", "            b */"), "x; /* a\n      b */"),
                    Arguments.of("x; ", 3, List.of("/* a", "  b */"), "x; /* a\n    b */"),
                    Arguments.of("    ", 6, List.of("/*", "       * a", "       */"), "    /*\n     * a\n     */"));
        }

        @ParameterizedTest(name = "{3}")
        @MethodSource("placements")
        void continuationLinesFollowTheOpenerOrTheIndent(final String before, final int sourceCol, final List<String> lines, final String expected) {
            assertThat(printAfter(before, lines, sourceCol)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{3}")
        @MethodSource("placements")
        void printingTheOutputAgainIsStable(final String before, final int sourceCol, final List<String> lines, final String expected) {
            final var printedLines = List.of(expected.split("\n", -1));
            final var opener = printedLines.getFirst().indexOf("/*");
            final var reparsed = Stream.concat(Stream.of(printedLines.getFirst().substring(opener)), printedLines.stream().skip(1)).toList();
            assertThat(printAfter(before, reparsed, opener)).isEqualTo(expected);
        }

        private static String printAfter(final String before, final List<String> lines, final int sourceCol) {
            return new Printer(80).print(new Doc.Indent(new Doc.Concat(List.of(new Doc.Text(before), new Doc.BlockComment(lines, sourceCol)))));
        }
    }

    @Nested
    class VerbatimTests {

        // A text block as it would appear in source at column 8:
        //         """
        //         hello
        //         world
        //         """
        // The string captured by Verbatim begins at the opening triple-quote and ends at the
        // closing one. When the printer emits this verbatim at a different output column we
        // expect a uniform shift on every line after the first.
        private static String textBlock(final int indent) {
            final var pad = " ".repeat(indent);
            return "\"\"\"\n" + pad + "hello\n" + pad + "world\n" + pad + "\"\"\"";
        }

        @Test
        void verbatim_emittedAtSourceColumn_isByteIdentical() {
            final var sourceCol = 8;
            final var block = textBlock(sourceCol);
            // simulate the source: 8 spaces of indent, then the literal
            final var doc = new Doc.Concat(List.of(new Doc.Text(" ".repeat(sourceCol)), new Doc.Verbatim(block, sourceCol)));
            assertThat(new Printer(200).print(doc)).isEqualTo(" ".repeat(sourceCol) + block);
        }

        @Test
        void verbatim_shiftedRight_prependsSpacesToSubsequentLines() {
            // source column 4, emit at column 10 -> shift = +6, every later line gains 6 leading spaces
            final var sourceCol = 4;
            final var outputCol = 10;
            final var block = textBlock(sourceCol);
            final var doc = new Doc.Concat(List.of(new Doc.Text(" ".repeat(outputCol)), new Doc.Verbatim(block, sourceCol)));
            final var expected = " ".repeat(outputCol) + textBlock(sourceCol + (outputCol - sourceCol));
            assertThat(new Printer(200).print(doc)).isEqualTo(expected);
        }

        @Test
        void verbatim_shiftedLeftWithinSafeLimit_stripsLeadingSpaces() {
            // source column 8, emit at column 4 -> shift = -4. content lines have 8 leading spaces,
            // closing """ has 8 leading spaces, so -4 is safe (we never strip more than 8).
            final var sourceCol = 8;
            final var outputCol = 4;
            final var block = textBlock(sourceCol);
            final var doc = new Doc.Concat(List.of(new Doc.Text(" ".repeat(outputCol)), new Doc.Verbatim(block, sourceCol)));
            final var expected = " ".repeat(outputCol) + textBlock(sourceCol + (outputCol - sourceCol));
            assertThat(new Printer(200).print(doc)).isEqualTo(expected);
        }

        @Test
        void verbatim_shiftedLeftBelowOutputCol_clampsContentRightOfOpener() {
            // Negative shift can't push content past the output """ column.
            final var rawBlock = "\"\"\"\n        hello\n        world\n        \"\"\"";
            final var doc = new Doc.Concat(List.of(new Doc.Text("    "), new Doc.Verbatim(rawBlock, 16)));
            assertThat(new Printer(200).print(doc)).isEqualTo("    \"\"\"\n    hello\n    world\n    \"\"\"");
        }

        @Test
        void verbatim_shiftedLeftBeyondSafeLimit_clampsToMinLeadingWhitespace() {
            // sourceCol = 4, outputCol = 0, requested shift = -4. The block's content lines and
            // closing """ each have only 2 leading spaces, so minLeadingWs = 2. The printer must
            // clamp the shift to -2 (otherwise the text-block content would lose more whitespace
            // than the minimum line has, changing the parsed string).
            final var rawBlock = "\"\"\"\n  hello\n  world\n  \"\"\"";
            final var doc = new Doc.Concat(List.of(new Doc.Verbatim(rawBlock, 4)));
            // result: each subsequent line loses 2 leading spaces (the maximum we can safely strip)
            final var expected = "\"\"\"\nhello\nworld\n\"\"\"";
            assertThat(new Printer(200).print(doc)).isEqualTo(expected);
        }

        @Test
        void verbatim_singleLine_treatedAsText() {
            // No newlines means no shift applies and no clamping concerns.
            final var doc = new Doc.Verbatim("\"x\"", 0);
            assertThat(new Printer(80).print(doc)).isEqualTo("\"x\"");
        }

        @Test
        void verbatim_updatesColumnToReflectShiftedLastLine() {
            // After verbatim, col should be the actual length of the (shifted) last line. A
            // following Text appended on the same logical line must not double-indent.
            final var sourceCol = 8;
            final var outputCol = 4;
            final var block = textBlock(sourceCol);
            final var doc = new Doc.Concat(List.of(new Doc.Text(" ".repeat(outputCol)), new Doc.Verbatim(block, sourceCol), new Doc.Text(";")));
            // last line after shift: "    \"\"\"" (4 spaces + """), then ";"
            assertThat(new Printer(200).print(doc)).isEqualTo(" ".repeat(outputCol) + textBlock(outputCol) + ";");
        }
    }

    @Nested
    class TrailingWhitespaceTests {

        @ParameterizedTest
        @MethodSource("trailingWhitespaceCases")
        void trailingWhitespace_strippedOutsideVerbatim(final Doc doc, final String expected) {
            assertThat(new Printer(200).print(doc)).isEqualTo(expected);
        }

        static Stream<Arguments> trailingWhitespaceCases() {
            final var textBlock = "\"\"\"\n        a  \n\n        b \t\n    \t\n        \"\"\"";
            return Stream.of(
                    Arguments.of(
                            new Doc.Concat(List.of(new Doc.Text("x = "), new Doc.Verbatim(textBlock, 4), new Doc.Text(";"))),
                            "x = " + textBlock + ";"),
                    Arguments.of(
                            new Doc.Indent(
                                    new Doc.Concat(
                                            List.of(
                                                    new Doc.Text("{"),
                                                    Doc.HARD_LINE,
                                                    new Doc.Text("a"),
                                                    Doc.BLANK_LINE,
                                                    Doc.BLANK_LINE,
                                                    new Doc.Text("b")))),
                            "{\n    a\n\n\n\n    b"),
                    Arguments.of(new Doc.Concat(List.of(new Doc.Text("a \t "), Doc.HARD_LINE, new Doc.Text("b  "))), "a\nb"),
                    Arguments.of(new Doc.Concat(List.of(new Doc.Verbatim("// c  ", 0), Doc.HARD_LINE, new Doc.Text("x"))), "// c  \nx"),
                    Arguments.of(new Doc.Concat(List.of(new Doc.Verbatim("x ", 0), new Doc.Text("  "), Doc.HARD_LINE, new Doc.Text("y"))), "x   \ny"),
                    Arguments.of(new Doc.Concat(List.of(new Doc.Verbatim("ab", 0), new Doc.Text("  "), Doc.HARD_LINE, new Doc.Text("y"))), "ab\ny"),
                    Arguments.of(new Doc.Concat(List.of(new Doc.Text("a  "), new Doc.Verbatim("\nb ", -1))), "a\nb "),
                    Arguments.of(new Doc.Concat(List.of(new Doc.Verbatim("p ", 0), new Doc.Text(" "), new Doc.Verbatim("\nb", -1))), "p  \nb"),
                    Arguments.of(new Doc.Concat(List.of(new Doc.Text("a "), new Doc.Verbatim("", 0), Doc.HARD_LINE)), "a\n"),
                    Arguments.of(
                            new Doc.Concat(List.of(new Doc.Verbatim("\"\"\"\n  q\n  \"\"\"", 0), new Doc.Text("  "), Doc.HARD_LINE)),
                            "\"\"\"\n  q\n  \"\"\"\n"));
        }
    }

    @Nested
    class LookaheadAwareFitDecision {

        // When deciding whether a Group can render FLAT, the printer counts what follows the
        // group on the same line, so a suffix that would overflow breaks the group.
        @Test
        void groupBreaks_whenSuffixWouldOverflowFlatLine() {
            // Group's flat form is "aa bb" (5). With lineWidth=7 and no suffix the group fits.
            // But the suffix " cc" pushes total to 8 > 7. The group breaks.
            final var doc = new Doc.Concat(
                    List.of(new Doc.Group(new Doc.Concat(List.of(new Doc.Text("aa"), Doc.LINE, new Doc.Text("bb")))), new Doc.Text(" cc")));
            assertThat(new Printer(7).print(doc)).isEqualTo("aa\nbb cc");
        }

        @Test
        void groupFlat_whenSuffixFitsTogether() {
            // Same shape, lineWidth=8: group flat "aa bb" + suffix " cc" = 8 ≤ 8, fits flat.
            final var doc = new Doc.Concat(
                    List.of(new Doc.Group(new Doc.Concat(List.of(new Doc.Text("aa"), Doc.LINE, new Doc.Text("bb")))), new Doc.Text(" cc")));
            assertThat(new Printer(8).print(doc)).isEqualTo("aa bb cc");
        }

        @Test
        void scanStops_atForcedBreakInUpcomingFrames() {
            // After the candidate group there is a HardLine — the current line ends there,
            // so the suffix Text("xxx") doesn't count against the candidate's fit decision.
            final var doc = new Doc.Concat(
                    List.of(
                            new Doc.Group(new Doc.Concat(List.of(new Doc.Text("aa"), Doc.LINE, new Doc.Text("bb")))),
                            Doc.HARD_LINE,
                            new Doc.Text("xxxxxxxxxx")));
            assertThat(new Printer(7).print(doc)).isEqualTo("aa bb\nxxxxxxxxxx");
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void group_whenColAlreadyExceedsLineWidth_alwaysBreaks() {
            // col=6 after Text("123456"), lineWidth=5 -> remaining=-1, group always breaks
            final var doc = new Doc.Concat(
                    List.of(new Doc.Text("123456"), new Doc.Group(new Doc.Concat(List.of(new Doc.Text("a"), Doc.LINE, new Doc.Text("b"))))));
            assertThat(new Printer(5).print(doc)).isEqualTo("123456a\nb");
        }
    }

    @Nested
    class ComplexTests {

        private static Doc blockDoc() {
            return new Doc.Group(
                    new Doc.Concat(
                            List.of(
                                    new Doc.Text("{"),
                                    new Doc.Indent(new Doc.Concat(List.of(Doc.LINE, new Doc.Text("x: 1,"), Doc.LINE, new Doc.Text("y: 2")))),
                                    Doc.LINE,
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
            assertThatThrownBy(() -> new Printer(0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void lineWidth_negative_throws() {
            assertThatThrownBy(() -> new Printer(-1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void lineWidth_one_isValid() {
            assertThat(new Printer(1).print(new Doc.Text("x"))).isEqualTo("x");
        }
    }

    @Nested
    class ScalingTests {

        private static Doc nestedGroups(final int n) {
            var inner = (Doc) new Doc.Text("x");
            for (var i = 0; i < n; i++) {
                inner = new Doc.Group(new Doc.Concat(List.of(new Doc.Text("g"), Doc.LINE, inner)));
            }
            return inner;
        }

        @Test
        @Timeout(value = 2, unit = TimeUnit.SECONDS)
        void deeplyNestedGroupDecisions_runInLinearTime() {
            // Each nested Group must make its own fit decision (mode=BREAK propagates inward
            // because lineWidth=1 forces every Group to break). A naive flat-width walker
            // re-traverses the entire remaining subtree per Group -> O(n²); a short-circuiting
            // fits(remaining) predicate stops at the first overflow -> O(n) total. The bound
            // here is generous (well under a second under O(n), many seconds under O(n²)) so
            // it fails loudly if a future change reintroduces full-subtree walks for fit checks.
            final var n = 50_000;
            final var rendered = new Printer(1).print(nestedGroups(n));
            assertThat(rendered).hasSize(2 * n + 1);
        }
    }
}
