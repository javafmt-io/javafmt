package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Diagnostic;
import io.javafmt.Javafmt;
import io.javafmt.Position;
import io.javafmt.parser.JavaParser;
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
        final var result = new FinalParameters().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        final var warning = result.diagnostics().getFirst();
        assertThat(warning).isInstanceOf(Diagnostic.Warning.class);
        assertThat(warning.message()).contains("'x'").contains("AtomicInteger");
        assertThat(warning.position()).isInstanceOf(Position.At.class);
        assertThat(((Position.At) warning.position()).line()).isEqualTo(3);
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
        final var result = Javafmt.formatWithResult(src);
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
        final var result = new FinalParameters().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void skipsRecordCompactConstructorParameters() {
        // A record's compact canonical constructor exposes the record components as
        // MethodTree#getParameters(). Inserting `final ` in front of them is a syntax
        // error — record components cannot have modifiers per JLS — so the rule must
        // skip compact constructors entirely.
        final var unit = JavaParser.parseUnit("""
            import java.util.List;
            import java.util.Objects;
            record Fixture(String output, List<String> diagnostics) {

                Fixture {
                    Objects.requireNonNull(output, "output");
                    diagnostics = List.copyOf(diagnostics);
                }
            }
            """);
        final var result = new FinalParameters().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }
}
