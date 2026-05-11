package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class CovariantEqualsTest {

    @Test
    void covariantEqualsWithSpecificTypeProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                public boolean equals(final Fixture other) {
                    return true;
                }
            }
            """);
        final var result = new CovariantEquals().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0)).isInstanceOf(Diagnostic.Warning.class);
        assertThat(result.diagnostics().get(0).message()).containsIgnoringCase("equals");
    }

    @Test
    void equalsWithObjectParamProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                @Override
                public boolean equals(final Object other) {
                    return true;
                }
            }
            """);
        final var result = new CovariantEquals().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void equalsWithNoParamProducesNoWarning() {
        // equals() with no params is not an overload we care about
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                public boolean equals() {
                    return true;
                }
            }
            """);
        final var result = new CovariantEquals().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void covariantEqualsWithPrimitiveParamProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                public boolean equals(final int x) {
                    return x == 0;
                }
            }
            """);
        final var result = new CovariantEquals().apply(unit);
        assertThat(result.diagnostics()).hasSize(1);
    }
}
