package io.github.jschneidereit.grind.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.Grind;
import io.github.jschneidereit.grind.parser.JavaParser;

import org.junit.jupiter.api.Test;

class FallThroughTest {

    @Test
    void caseEndingInBreakProducesNoDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            doA();
                            break;
                        case 2:
                            doB();
                            break;
                    }
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void caseEndingInReturnProducesNoDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int test(final int x) {
                    switch (x) {
                        case 1:
                            return 1;
                        case 2:
                            return 2;
                    }
                    return 0;
                }
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void caseEndingInThrowProducesNoDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            throw new IllegalArgumentException();
                        case 2:
                            throw new IllegalStateException();
                    }
                }
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void caseEndingInContinueProducesNoDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    while (x > 0) {
                        switch (x) {
                            case 1:
                                continue;
                            case 2:
                                continue;
                        }
                    }
                }
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void emptyCaseBodyForLabelGroupingProducesNoDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                        case 2:
                            doStuff();
                            break;
                    }
                }
                native void doStuff();
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void lastCaseUnterminatedProducesNoDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            doA();
                            break;
                        case 2:
                            doB();
                    }
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void plainFallThroughProducesOneDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            doA();
                        case 2:
                            doB();
                            break;
                    }
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        final var d = result.diagnostics().get(0);
        assertThat(d).isInstanceOf(Diagnostic.LintError.class);
        assertThat(d.isError()).isTrue();
        assertThat(d.message()).contains("falls through");
    }

    @Test
    void multipleFallThroughsProduceMultipleDiagnostics() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            doA();
                        case 2:
                            doB();
                        case 3:
                            doC();
                            break;
                    }
                }
                native void doA();
                native void doB();
                native void doC();
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).hasSize(2);
        assertThat(result.diagnostics()).allMatch(d -> d instanceof Diagnostic.LintError);
    }

    @Test
    void fallthroughCommentSuppressesDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            doA();
                            // fallthrough
                        case 2:
                            doB();
                            break;
                    }
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void fallThroughWithSpaceCommentSuppressesDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            doA();
                            // fall through
                        case 2:
                            doB();
                            break;
                    }
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void dollarFallThroughDollarSuppressesDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            doA();
                            // $FALL-THROUGH$
                        case 2:
                            doB();
                            break;
                    }
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void blockBodyEndingInReturnProducesNoDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int test(final int x) {
                    switch (x) {
                        case 1: { doA(); return 1; }
                        case 2:
                            return 2;
                    }
                    return 0;
                }
                native void doA();
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void arrowFormSwitchProducesNoDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1 -> doA();
                        case 2 -> doB();
                    }
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void switchExpressionWithYieldProducesNoDiagnostic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int test(final int x) {
                    return switch (x) {
                        case 1:
                            yield 1;
                        case 2:
                            yield 2;
                        default:
                            yield 0;
                    };
                }
            }
            """);
        final var result = new FallThrough().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void endToEndFormatPropagatesLintError() {
        final var src = """
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            doA();
                        case 2:
                            doB();
                            break;
                    }
                }
                native void doA();
                native void doB();
            }
            """;
        final var result = Grind.formatWithResult(src);
        assertThat(result.diagnostics()).anyMatch(d -> d instanceof Diagnostic.LintError);
        assertThat(result.hasErrors()).isTrue();
    }
}
