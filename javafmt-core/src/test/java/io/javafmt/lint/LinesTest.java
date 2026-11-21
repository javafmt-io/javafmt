package io.javafmt.lint;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinesTest {

    private static final String SRC = "ab\n  cd\tef\ngh";

    @Test
    void startIsTheOffsetAfterThePreviousNewline() {
        assertThat(Lines.start(SRC, 0)).isZero();
        assertThat(Lines.start(SRC, 2)).isZero();
        assertThat(Lines.start(SRC, 3)).isEqualTo(3);
        assertThat(Lines.start(SRC, 6)).isEqualTo(3);
        assertThat(Lines.start(SRC, SRC.length())).isEqualTo(11);
    }

    @Test
    void endIsAfterTheNextNewlineOrAtTheEndOfTheSource() {
        assertThat(Lines.end(SRC, 0)).isEqualTo(3);
        assertThat(Lines.end(SRC, 2)).isEqualTo(3);
        assertThat(Lines.end(SRC, 3)).isEqualTo(11);
        assertThat(Lines.end(SRC, 11)).isEqualTo(SRC.length());
        assertThat(Lines.end(SRC, SRC.length())).isEqualTo(SRC.length());
    }

    @Test
    void indentIsTheLeadingBlankRunBeforeThePosition() {
        assertThat(Lines.indentAt(SRC, 0)).isEmpty();
        assertThat(Lines.indentAt(SRC, 8)).isEqualTo("  ");
        assertThat(Lines.indentAt(SRC, 5)).isEqualTo("  ");
        assertThat(Lines.indentAt(SRC, SRC.length())).isEmpty();
    }

    @Test
    void columnCountsCharactersSinceTheLineStart() {
        assertThat(Lines.column(SRC, 0)).isZero();
        assertThat(Lines.column(SRC, 7)).isEqualTo(4);
        assertThat(Lines.column(SRC, SRC.length())).isEqualTo(2);
    }

    @Test
    void aSourceWithoutNewlineIsOneLine() {
        final var src = "  abc";
        assertThat(Lines.start(src, 4)).isZero();
        assertThat(Lines.end(src, 1)).isEqualTo(src.length());
        assertThat(Lines.indentAt(src, 5)).isEqualTo("  ");
        assertThat(Lines.column(src, 5)).isEqualTo(5);
    }
}
