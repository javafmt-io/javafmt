package io.javafmt.lint;

import io.javafmt.Javafmt;
import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The trailing comma a wide enum gets is a source edit, not a rendering decision: the output
 * verifier compares the printed text against the post-lint source, so a comma the renderer invented
 * would look exactly like corruption and every wide enum without one came back refused.
 */
class EnumTrailingCommaTest {

    private static final String WIDE = "enum Big { ALPHA, BRAVO, CHARLIE, DELTA, ECHO, FOXTROT, GOLF, HOTEL, INDIA, JULIET, KILO, LIMA, "
            + "MIKE, NOVEMBER, OSCAR, PAPA, QUEBEC, ROMEO, SIERRA, TANGO, UNIFORM }\n";

    private static LintResult lint(final String source) {
        return new EnumTrailingComma().apply(LintContext.parseOnly(JavaParser.parseUnit(source)));
    }

    @Test
    void anEnumTooWideForOneLineGetsATrailingComma() {
        assertThat(lint(WIDE).edits()).singleElement().satisfies(edit -> assertThat(edit.replacement()).isEqualTo(","));
    }

    @Test
    void anEnumThatFitsOnOneLineGetsATrailingCommaToo() {
        assertThat(lint("enum Small { A, B }\n").edits()).singleElement().satisfies(edit -> assertThat(edit.replacement()).isEqualTo(","));
    }

    @Test
    void anEnumTheAuthorAlreadyPunctuatedIsLeftAlone() {
        assertThat(lint(WIDE.replace("UNIFORM }", "UNIFORM, }")).edits()).isEmpty();
        assertThat(lint(WIDE.replace("UNIFORM }", "UNIFORM; }")).edits()).isEmpty();
    }

    @Test
    void theCommaGoesAfterEveryBlockCommentBesideTheLastConstant() {
        final var source = "enum Kind { FIRST, LAST /* three */ /* four */ }\n";
        assertThat(LintEngine.applyEdits(source, lint(source).edits())).isEqualTo("enum Kind { FIRST, LAST /* three */ /* four */, }\n");
    }

    @Test
    void anEnumWithBodyMembersGetsNothing() {
        // The `;` after the constants is mandatory there, so there is no separator to add.
        assertThat(lint("enum WithBody { A, B; void m() {} }\n").edits()).isEmpty();
    }

    @Test
    void wideEnumEndsUpWithATrailingComma() {
        assertThat(Javafmt.format(WIDE)).contains("    UNIFORM,\n}");
    }

    @Test
    void wideEnumFormatsWithoutBeingRefused() {
        assertThat(Javafmt.formatWithResult(WIDE).diagnostics()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"enum Small { A, B }\n", "enum Small { A, B, }\n", "enum Small {\n    A,\n    B,\n}\n"})
    void narrowEnumStaysOnOneLineWithoutAComma(final String source) {
        assertThat(Javafmt.format(source)).isEqualTo("enum Small { A, B }\n");
    }

    @Test
    void aCommaWrittenInFrontOfTheSemicolonIsRemoved() {
        // Both marks are legal and both are redundant, but the renderer emits one separator, so
        // leaving the pair in the source leaves the output a token short and the file refused.
        final var source = "enum WithBody { A, B,; void m() { call(); } }\n";
        assertThat(Javafmt.formatWithResult(source).diagnostics()).isEmpty();
        assertThat(Javafmt.format(source)).contains("    B;");
    }

    @ParameterizedTest
    @ValueSource(strings = {"enum WithBody { A, B /* c */,; void m() { call(); } }\n", "enum E { A, B /* c */,; }\n"})
    void aCommaAfterACommentInFrontOfTheSemicolonFormatsWithoutBeingRefused(final String source) {
        assertThat(Javafmt.formatWithResult(source).diagnostics()).isEmpty();
    }

    @Test
    void aBodylessEnumWithBothMarksFormatsWithoutBeingRefused() {
        assertThat(Javafmt.formatWithResult("enum E { A, B,; }\n").diagnostics()).isEmpty();
    }
}
