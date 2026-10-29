package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

class StringLiteralEqualityTest {

    @Test
    void stringLiteralEqualityProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                boolean test(final String s) {
                    return s == "hello";
                }
            }
            """);
        final var result = new StringLiteralEquality().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst()).isInstanceOf(Diagnostic.Warning.class);
        assertThat(result.diagnostics().getFirst().message()).containsIgnoringCase("==");
    }

    @Test
    void stringLiteralNotEqualityProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                boolean test(final String s) {
                    return s != "hello";
                }
            }
            """);
        final var result = new StringLiteralEquality().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).hasSize(1);
    }

    @Test
    void literalOnLeftProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                boolean test(final String s) {
                    return "hello" == s;
                }
            }
            """);
        final var result = new StringLiteralEquality().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).hasSize(1);
    }

    @Test
    void intLiteralEqualityProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                boolean test(final int x) {
                    return x == 0;
                }
            }
            """);
        final var result = new StringLiteralEquality().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void nullComparisonProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                boolean test(final Object x) {
                    return x == null;
                }
            }
            """);
        final var result = new StringLiteralEquality().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void equalsMethodCallProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                boolean test(final String s) {
                    return s.equals("hello");
                }
            }
            """);
        final var result = new StringLiteralEquality().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).isEmpty();
    }
}
