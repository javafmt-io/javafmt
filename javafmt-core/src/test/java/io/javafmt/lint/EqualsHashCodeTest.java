package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class EqualsHashCodeTest {

    @Test
    void classWithEqualsButNoHashCodeProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                @Override
                public boolean equals(final Object other) {
                    return true;
                }
            }
            """);
        final var result = new EqualsHashCode().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0)).isInstanceOf(Diagnostic.Warning.class);
        assertThat(result.diagnostics().get(0).message()).contains("equals");
        assertThat(result.diagnostics().get(0).message()).contains("hashCode");
    }

    @Test
    void classWithHashCodeButNoEqualsProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                @Override
                public int hashCode() {
                    return 42;
                }
            }
            """);
        final var result = new EqualsHashCode().apply(unit);
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0)).isInstanceOf(Diagnostic.Warning.class);
    }

    @Test
    void classWithBothEqualsAndHashCodeProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                @Override
                public boolean equals(final Object other) {
                    return true;
                }
                @Override
                public int hashCode() {
                    return 42;
                }
            }
            """);
        final var result = new EqualsHashCode().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void classWithNeitherProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void doSomething() {}
            }
            """);
        final var result = new EqualsHashCode().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }
}
