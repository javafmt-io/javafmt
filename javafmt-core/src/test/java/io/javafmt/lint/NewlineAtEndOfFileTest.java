package io.javafmt.lint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NewlineAtEndOfFileTest {

    @Test
    void fileWithoutTrailingNewlineGetsOneInserted() {
        final var result = LintFixtures.apply(new NewlineAtEndOfFile(), "class Fixture {}");
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().start()).isEqualTo(16);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(LintFixtures.fix(new NewlineAtEndOfFile(), "class Fixture {}"))
                .isEqualTo("class Fixture {}\n");
    }

    @Test
    void fileWithExactlyOneTrailingNewlineEmitsNoEdits() {
        assertThat(
                        LintFixtures.apply(new NewlineAtEndOfFile(), "class Fixture {}\n")
                                .edits())
                .isEmpty();
        assertThat(LintFixtures.fix(new NewlineAtEndOfFile(), "class Fixture {}\n"))
                .isEqualTo("class Fixture {}\n");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"2|17|18", "3|17|19"})
    void extraTrailingNewlinesAreTrimmedToOne(final int newlines, final int start, final int end) {
        final var source = "class Fixture {}" + "\n".repeat(newlines);
        final var result = LintFixtures.apply(new NewlineAtEndOfFile(), source);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().start()).isEqualTo(start);
        assertThat(result.edits().getFirst().end()).isEqualTo(end);
        assertThat(LintFixtures.fix(new NewlineAtEndOfFile(), source)).isEqualTo("class Fixture {}\n");
    }
}
