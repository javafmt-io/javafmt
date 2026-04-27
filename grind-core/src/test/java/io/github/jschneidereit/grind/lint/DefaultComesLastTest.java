package io.github.jschneidereit.grind.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.Grind;
import io.github.jschneidereit.grind.parser.JavaParser;

import org.junit.jupiter.api.Test;

class DefaultComesLastTest {

    @Test
    void defaultLastEmitsNothing() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1 -> doA();
                        default -> doB();
                    }
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new DefaultComesLast().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void noDefaultEmitsNothing() {
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
        final var result = new DefaultComesLast().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void arrowFormDefaultInMiddleEmitsTwoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1 -> doA();
                        default -> doB();
                        case 2 -> doC();
                    }
                }
                native void doA();
                native void doB();
                native void doC();
            }
            """);
        final var result = new DefaultComesLast().apply(unit);
        assertThat(result.edits()).hasSize(2);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void arrowFormDefaultFirstEmitsTwoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        default -> doDefault();
                        case 1 -> doA();
                        case 2 -> doB();
                    }
                }
                native void doDefault();
                native void doA();
                native void doB();
            }
            """);
        final var result = new DefaultComesLast().apply(unit);
        assertThat(result.edits()).hasSize(2);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void colonFormFullySafeEmitsTwoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            doA();
                            break;
                        default:
                            doDefault();
                            break;
                        case 2:
                            doB();
                            break;
                    }
                }
                native void doA();
                native void doB();
                native void doDefault();
            }
            """);
        final var result = new DefaultComesLast().apply(unit);
        assertThat(result.edits()).hasSize(2);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void colonFormLabelGroupingWithDefaultWarnsOnly() {
        // case 1 (empty) followed by default: doStuff(); break; — predecessor is empty,
        // so moving default would break the label group.
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                        default:
                            doStuff();
                            break;
                        case 2:
                            doOther();
                            break;
                    }
                }
                native void doStuff();
                native void doOther();
            }
            """);
        final var result = new DefaultComesLast().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0)).isInstanceOf(Diagnostic.Warning.class);
        assertThat(result.diagnostics().get(0).message()).contains("default case should be last");
    }

    @Test
    void colonFormDefaultEmptyBodyWarnsOnly() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            doA();
                            break;
                        default:
                        case 2:
                            doB();
                            break;
                    }
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new DefaultComesLast().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0)).isInstanceOf(Diagnostic.Warning.class);
    }

    @Test
    void colonFormDefaultBodyDoesNotTerminateWarnsOnly() {
        // Note: this would also normally trip FallThrough, but DefaultComesLast must
        // independently verify the terminator condition itself.
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                            doA();
                            break;
                        default:
                            doDefault();
                        case 2:
                            doB();
                            break;
                    }
                }
                native void doA();
                native void doB();
                native void doDefault();
            }
            """);
        final var result = new DefaultComesLast().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0)).isInstanceOf(Diagnostic.Warning.class);
    }

    @Test
    void endToEndArrowFormAutoFixMovesDefaultLast() {
        final var src = """
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1 -> doA();
                        default -> doDefault();
                        case 2 -> doB();
                    }
                }
                native void doA();
                native void doB();
                native void doDefault();
            }
            """;
        final var output = Grind.format(src);
        final var defaultIdx = output.indexOf("default ->");
        final var case1Idx = output.indexOf("case 1");
        final var case2Idx = output.indexOf("case 2");
        assertThat(defaultIdx).isGreaterThan(case1Idx);
        assertThat(defaultIdx).isGreaterThan(case2Idx);
    }

    @Test
    void endToEndWarnOnlyExposesDiagnostic() {
        final var src = """
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1:
                        default:
                            doStuff();
                            break;
                        case 2:
                            doOther();
                            break;
                    }
                }
                native void doStuff();
                native void doOther();
            }
            """;
        final var result = Grind.formatWithResult(src);
        assertThat(result.diagnostics())
            .anyMatch(d -> d instanceof Diagnostic.Warning
                && d.message().contains("default case should be last"));
    }

    @Test
    void formatIsIdempotent() {
        final var src = """
            class Fixture {
                void test(final int x) {
                    switch (x) {
                        case 1 -> doA();
                        default -> doDefault();
                        case 2 -> doB();
                    }
                }
                native void doA();
                native void doB();
                native void doDefault();
            }
            """;
        final var first = Grind.format(src);
        final var second = Grind.format(first);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void twoSwitchesOneAutoFixOneWarn() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void a(final int x) {
                    switch (x) {
                        case 1 -> doA();
                        default -> doD();
                        case 2 -> doB();
                    }
                }
                void b(final int x) {
                    switch (x) {
                        case 1:
                        default:
                            doStuff();
                            break;
                        case 2:
                            doOther();
                            break;
                    }
                }
                native void doA();
                native void doB();
                native void doD();
                native void doStuff();
                native void doOther();
            }
            """);
        final var result = new DefaultComesLast().apply(unit);
        assertThat(result.edits()).hasSize(2);
        assertThat(result.diagnostics()).hasSize(1);
    }
}
