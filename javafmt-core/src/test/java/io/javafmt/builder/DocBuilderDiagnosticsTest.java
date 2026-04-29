package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Diagnostic;
import io.javafmt.Javafmt;
import io.javafmt.Position;
import io.javafmt.JavafmtConfig;
import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class DocBuilderDiagnosticsTest {

    @Test
    void cleanSourceProducesNoWarnings() {
        final var result = Javafmt.formatWithResult("class Foo { int x; }");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void bracelessIfBodyEmitsAddedBracesWarning() {
        final var source = """
                class Fixture {
                    void test(int x) {
                        if (x > 0) doStuff();
                    }

                    void doStuff() {}
                }
                """;
        final var unit = JavaParser.parseUnit(source);
        final var built = DocBuilder.buildWithFallbacks(unit, JavafmtConfig.defaults());

        final var braceWarnings = built.diagnostics().stream()
            .filter(d -> d instanceof Diagnostic.Warning)
            .filter(d -> d.message().contains("braces"))
            .toList();

        assertThat(braceWarnings).hasSize(1);
        assertThat(braceWarnings.get(0).position()).isInstanceOf(Position.At.class);
        final var pos = (Position.At) braceWarnings.get(0).position();
        assertThat(pos.line()).isEqualTo(3);
        assertThat(pos.column()).isPositive();
    }

    @Test
    void bracelessIfElseBodyEmitsTwoWarnings() {
        final var source = """
                class Fixture {
                    void test(int x) {
                        if (x > 0) doStuff();
                        else fallback();
                    }

                    void doStuff() {}
                    void fallback() {}
                }
                """;
        final var unit = JavaParser.parseUnit(source);
        final var built = DocBuilder.buildWithFallbacks(unit, JavafmtConfig.defaults());

        final var braceWarnings = built.diagnostics().stream()
            .filter(d -> d instanceof Diagnostic.Warning)
            .filter(d -> d.message().contains("braces"))
            .toList();

        assertThat(braceWarnings).hasSize(2);
    }

    @Test
    void bracelessLoopsEachEmitOneWarning() {
        final var source = """
                class Fixture {
                    void test(int n, int[] xs) {
                        for (int i = 0; i < n; i++) doStuff();
                        for (int x : xs) doStuff();
                        while (n > 0) n--;
                        do doStuff(); while (n > 0);
                    }

                    void doStuff() {}
                }
                """;
        final var unit = JavaParser.parseUnit(source);
        final var built = DocBuilder.buildWithFallbacks(unit, JavafmtConfig.defaults());

        final var braceWarnings = built.diagnostics().stream()
            .filter(d -> d instanceof Diagnostic.Warning)
            .filter(d -> d.message().contains("braces"))
            .toList();

        assertThat(braceWarnings).hasSize(4);
        assertThat(braceWarnings).allSatisfy(w -> {
            assertThat(w.position()).isInstanceOf(Position.At.class);
            assertThat(((Position.At) w.position()).line()).isPositive();
        });
    }

    @Test
    void alreadyBracedConstructsEmitNoBraceWarnings() {
        final var source = """
                class Fixture {
                    void test(int x) {
                        if (x > 0) {
                            doStuff();
                        } else {
                            fallback();
                        }
                        for (int i = 0; i < x; i++) {
                            doStuff();
                        }
                        while (x > 0) {
                            x--;
                        }
                    }

                    void doStuff() {}
                    void fallback() {}
                }
                """;
        final var result = Javafmt.formatWithResult(source);
        final var braceWarnings = result.diagnostics().stream()
            .filter(d -> d instanceof Diagnostic.Warning)
            .filter(d -> d.message().contains("braces"))
            .toList();
        assertThat(braceWarnings).isEmpty();
    }
}
