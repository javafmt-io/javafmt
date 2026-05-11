package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class NewlineAtEndOfFileTest {

    @Test
    void fileWithoutTrailingNewlineGetsOneInserted() {
        final var unit = JavaParser.parseUnit("class Fixture {}");
        final var result = new NewlineAtEndOfFile().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("\n");
        assertThat(result.edits().get(0).start()).isEqualTo(16);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void fileWithExactlyOneTrailingNewlineEmitsNoEdits() {
        final var unit = JavaParser.parseUnit("class Fixture {}\n");
        final var result = new NewlineAtEndOfFile().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void fileWithTwoTrailingNewlinesTrimmedToOne() {
        final var unit = JavaParser.parseUnit("class Fixture {}\n\n");
        final var result = new NewlineAtEndOfFile().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEmpty();
        // deletes the second newline
        assertThat(result.edits().get(0).start()).isEqualTo(17);
        assertThat(result.edits().get(0).end()).isEqualTo(18);
    }

    @Test
    void fileWithThreeTrailingNewlinesTrimmedToOne() {
        final var unit = JavaParser.parseUnit("class Fixture {}\n\n\n");
        final var result = new NewlineAtEndOfFile().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEmpty();
        assertThat(result.edits().get(0).start()).isEqualTo(17);
        assertThat(result.edits().get(0).end()).isEqualTo(19);
    }

    @Test
    void ruleIsIdempotentOnFileWithExistingNewline() {
        final var unit = JavaParser.parseUnit("class Fixture {}\n");
        final var result = new NewlineAtEndOfFile().apply(unit);
        assertThat(result.edits()).isEmpty();
    }
}
