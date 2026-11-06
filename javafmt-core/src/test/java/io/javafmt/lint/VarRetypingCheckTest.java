package io.javafmt.lint;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class VarRetypingCheckTest {

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5})
    void anOffsetMovesByEveryEditBeforeIt(final long seed) {
        final var random = new Random(seed);
        final var edits = IntStream.range(0, 40)
                .mapToObj(i -> i * 25 + random.nextInt(10))
                .map(start -> random.nextBoolean() ? LintEdit.insert(start, "final ") : new LintEdit(start, start + random.nextInt(8), "var"))
                .toList();
        final var shift = new VarRetypingCheck.Shift(edits);
        IntStream.range(0, 1100).forEach(offset -> {
            assertThat(shift.start(offset)).as("start of %d", offset).isEqualTo(offset + movedBy(edits, offset, true));
            assertThat(shift.end(offset)).as("end of %d", offset).isEqualTo(offset + movedBy(edits, offset, false));
        });
    }

    private static int movedBy(final List<LintEdit> edits, final int offset, final boolean start) {
        return edits.stream()
                .filter(edit -> edit.start() == edit.end() ? (start ? edit.start() <= offset : edit.start() < offset) : edit.end() <= offset)
                .mapToInt(edit -> edit.replacement().length() - (edit.end() - edit.start()))
                .sum();
    }
}
