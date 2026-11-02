package io.javafmt;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeIdempotencyTest {
    private static final JavafmtConfig ANALYZE = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(""));

    @ParameterizedTest
    @ValueSource(strings = {"""
            class Writer {
                void write(final int off, final int len) {
                    int t = off + len, u = 0;
                    System.out.println(t + u);
                }
            }
            """, """
            import java.util.List;

            class Basket {
                int count(final List<String> items) {
                    int size = items.size(), limit = 10;
                    return Math.min(size, limit);
                }
            }
            """})
    void formattingAgainWithTheClasspathChangesNothing(final String source) {
        final var once = Javafmt.formatWithResult(source, ANALYZE);
        assertThat(once.errors()).isEmpty();
        assertThat(Javafmt.formatWithResult(once.output(), ANALYZE).output()).isEqualTo(once.output());
    }

    @ParameterizedTest
    @ValueSource(strings = {"""
            class Writer {
                void write(final int off, final int len) {
                    int t = off + len, u = 0;
                    System.out.println(t + u);
                }
            }
            """})
    void aBatchFormattedAgainWithTheClasspathChangesNothing(final String source) {
        final var once = Javafmt.formatAll(List.of(source, "class Reader {}\n"), ANALYZE, 2);
        final var twice = Javafmt.formatAll(once.stream().map(FormatResult::output).toList(), ANALYZE, 2);
        assertThat(twice).isEqualTo(once.stream().map(result -> new FormatResult(result.output(), result.diagnostics())).toList());
    }
}
