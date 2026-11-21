package io.javafmt;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PositionTest {

    @ParameterizedTest
    @CsvSource({"0, 1, 1", "3, 2, 1", "5, 2, 3", "4, 2, 2", "7, 3, 1"})
    void atComputesOneBasedLineAndColumnFromAnOffset(final int offset, final int line, final int column) {
        assertThat(Position.at("ab\ncde\nf", offset)).isEqualTo(new Position.At(line, column, offset));
    }
}
