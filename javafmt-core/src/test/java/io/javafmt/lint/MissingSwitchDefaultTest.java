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
        final var result = new MissingSwitchDefault().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst()).isInstanceOf(Diagnostic.Warning.class);
        assertThat(result.diagnostics().getFirst().message()).containsIgnoringCase("default");
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
        final var result = new MissingSwitchDefault().apply(LintContext.parseOnly(unit));
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
        final var result = new MissingSwitchDefault().apply(LintContext.parseOnly(unit));
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
        final var result = new MissingSwitchDefault().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).hasSize(2);
    }

    @Test
    void anExhaustivePatternSwitchNeedsNoDefault() {
        // Since Java 21 the compiler checks a pattern switch over a sealed type for exhaustiveness.
        // Adding a default to one is how you stop it telling you about the subtype you forgot.
        final var unit = JavaParser.parseUnit("""
            sealed interface Shape permits Circle, Square {}

            record Circle(int r) implements Shape {}

            record Square(int s) implements Shape {}

            class Fixture {

                void test(final Shape shape) {
                    switch (shape) {
                        case Circle c -> report(c);
                        case Square s -> report(s);
                    }
                }

                void report(final Object o) {
                    call(o);
                }
            }
            """);
        assertThat(new MissingSwitchDefault().apply(LintContext.parseOnly(unit)).diagnostics()).isEmpty();
    }

    @Test
    void aConstantSwitchWithoutADefaultStillWarns() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test(final int x) {
                    switch (x) {
                        case 1 -> call();
                        case 2 -> call();
                    }
                }
            }
            """);
        assertThat(new MissingSwitchDefault().apply(LintContext.parseOnly(unit)).diagnostics()).hasSize(1);
    }
}
