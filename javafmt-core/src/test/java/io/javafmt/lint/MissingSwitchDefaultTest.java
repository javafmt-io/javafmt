package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class MissingSwitchDefaultTest {

    @Test
    void switchWithoutDefaultProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            break;
                        case 2:
                            break;
                    }
                }
            }
            """);
        final var result = new MissingSwitchDefault().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0)).isInstanceOf(Diagnostic.Warning.class);
        assertThat(result.diagnostics().get(0).message()).containsIgnoringCase("default");
    }

    @Test
    void switchWithDefaultProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            break;
                        default:
                            break;
                    }
                }
            }
            """);
        final var result = new MissingSwitchDefault().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void switchExpressionNotWarned() {
        // Switch expressions require exhaustiveness, so default is often implicit
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int test(final int x) {
                    return switch (x) {
                        case 1 -> 10;
                        default -> 0;
                    };
                }
            }
            """);
        final var result = new MissingSwitchDefault().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void multipleSwitchesEachWithoutDefaultProduceMultipleWarnings() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x, final int y) {
                    switch (x) {
                        case 1:
                            break;
                    }
                    switch (y) {
                        case 2:
                            break;
                    }
                }
            }
            """);
        final var result = new MissingSwitchDefault().apply(unit);
        assertThat(result.diagnostics()).hasSize(2);
    }
}
