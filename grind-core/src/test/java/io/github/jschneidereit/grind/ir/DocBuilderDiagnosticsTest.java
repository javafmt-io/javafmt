package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.Grind;
import io.github.jschneidereit.grind.GrindConfig;
import io.github.jschneidereit.grind.parser.JavaParser;

import org.junit.jupiter.api.Test;

class DocBuilderDiagnosticsTest {

    @Test
    void colonFormSwitchCaseProducesWarningWithPosition() {
        final var source = """
                class Fixture {
                    void test(int x) {
                        switch (x) {
                            case 1:
                                doStuff();
                                break;
                            default:
                                fallback();
                        }
                    }

                    void doStuff() {}
                    void fallback() {}
                }
                """;
        final var unit = JavaParser.parseUnit(source);
        final var built = DocBuilder.buildWithFallbacks(unit, GrindConfig.defaults());

        final var warnings = built.diagnostics().stream()
            .filter(d -> d instanceof Diagnostic.Warning)
            .toList();

        assertThat(warnings).isNotEmpty();
        assertThat(warnings).allSatisfy(w -> {
            assertThat(w.position().line()).isPositive();
            assertThat(w.position().column()).isPositive();
        });
        assertThat(warnings.get(0).message()).contains("CASE");
    }

    @Test
    void cleanSourceProducesNoWarnings() {
        final var result = Grind.formatWithResult("class Foo { int x; }");
        assertThat(result.diagnostics()).isEmpty();
    }
}
