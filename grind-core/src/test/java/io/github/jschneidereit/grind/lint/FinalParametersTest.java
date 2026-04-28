package io.github.jschneidereit.grind.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.Grind;
import io.github.jschneidereit.grind.Position;
import io.github.jschneidereit.grind.parser.JavaParser;

import org.junit.jupiter.api.Test;

class FinalParametersTest {

    @Test
    void emitsWarningAndSkipsEditWhenParameterIsReassigned() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(int x) {
                    do {
                        x--;
                    } while (x > 0);
                }
            }
            """);
        final var result = new FinalParameters().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        final var warning = result.diagnostics().get(0);
        assertThat(warning).isInstanceOf(Diagnostic.Warning.class);
        assertThat(warning.message()).contains("'x'").contains("AtomicInteger");
        assertThat(warning.position()).isInstanceOf(Position.At.class);
        assertThat(((Position.At) warning.position()).line()).isEqualTo(2);
    }

    @Test
    void formatPropagatesLintWarnings() {
        final var src = """
            class Fixture {
                void test(int x) {
                    while (x > 0) {
                        x--;
                    }
                }
            }
            """;
        final var result = Grind.formatWithResult(src);
        assertThat(result.diagnostics())
            .anySatisfy(d -> {
                assertThat(d).isInstanceOf(Diagnostic.Warning.class);
                assertThat(d.message()).contains("reassigned");
            });
    }

    @Test
    void emitsEditAndNoWarningWhenParameterIsNotReassigned() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int twice(int x) {
                    return x + x;
                }
            }
            """);
        final var result = new FinalParameters().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.diagnostics()).isEmpty();
    }
}
