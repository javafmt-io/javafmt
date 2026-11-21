package io.javafmt;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FormatStagesTest {

    @ParameterizedTest
    @CsvSource(value = {"'', ''", "'\n', ''", "x, 'x\n'", "'x\n', 'x\n'", "'x\n\n\n', 'x\n'", "'\n\nx\n\n', '\n\nx\n'"})
    void withOneTrailingNewlineKeepsExactlyOneFinalNewlineOnNonBlankText(final String printed, final String expected) {
        assertThat(FormatStages.withOneTrailingNewline(printed)).isEqualTo(expected);
    }
}
