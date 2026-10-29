package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Diagnostic;
import io.javafmt.Javafmt;
import io.javafmt.parser.JavaParser;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultComesLastTest {

    private static final String ARROW_FORM_AUTO_FIX = """
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

    private static final String COLON_FORM_AUTO_FIX = """
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
        """;

    private static final String COLON_FORM_WARN_ONLY = """
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
        final var result = new DefaultComesLast().apply(LintContext.parseOnly(unit));
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
        final var result = new DefaultComesLast().apply(LintContext.parseOnly(unit));
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
        final var result = new DefaultComesLast().apply(LintContext.parseOnly(unit));
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
        final var result = new DefaultComesLast().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(2);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void colonFormFullySafeEmitsTwoEdits() {
        final var unit = JavaParser.parseUnit(COLON_FORM_AUTO_FIX);
        final var result = new DefaultComesLast().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(2);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void colonFormLabelGroupingWithDefaultWarnsOnly() {
        // case 1 (empty) followed by default: doStuff(); break; — predecessor is empty,
        // so moving default would break the label group.
        final var unit = JavaParser.parseUnit(COLON_FORM_WARN_ONLY);
        final var result = new DefaultComesLast().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst()).isInstanceOf(Diagnostic.Warning.class);
        assertThat(result.diagnostics().getFirst().message()).contains("default case should be last");
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
        final var result = new DefaultComesLast().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst()).isInstanceOf(Diagnostic.Warning.class);
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
        final var result = new DefaultComesLast().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst()).isInstanceOf(Diagnostic.Warning.class);
    }

    @Test
    void endToEndArrowFormAutoFixMovesDefaultLast() {
        final var output = Javafmt.format(ARROW_FORM_AUTO_FIX);
        final var defaultIdx = output.indexOf("default ->");
        assertThat(defaultIdx).isGreaterThan(output.indexOf("case 1"));
        assertThat(defaultIdx).isGreaterThan(output.indexOf("case 2"));
    }

    @Test
    void endToEndWarnOnlyExposesDiagnostic() {
        final var result = Javafmt.formatWithResult(COLON_FORM_WARN_ONLY);
        assertThat(result.diagnostics()).anyMatch(d -> d instanceof Diagnostic.Warning && d.message().contains("default case should be last"));
    }

    @Test
    void endToEndColonFormAutoFixMovesDefaultLast() {
        final var output = Javafmt.format(COLON_FORM_AUTO_FIX);
        final var defaultIdx = output.indexOf("default:");
        assertThat(defaultIdx).isGreaterThan(output.indexOf("case 1:"));
        assertThat(defaultIdx).isGreaterThan(output.indexOf("case 2:"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(value = "idempotencyCases")
    void formatIsIdempotent(final String name, final String source) {
        final var first = Javafmt.format(source);
        assertThat(Javafmt.format(first)).isEqualTo(first);
    }

    static Stream<Arguments> idempotencyCases() {
        return Stream.of(
                Arguments.of("arrow-form-auto-fix", ARROW_FORM_AUTO_FIX),
                Arguments.of("colon-form-auto-fix", COLON_FORM_AUTO_FIX),
                Arguments.of("colon-form-warn-only", COLON_FORM_WARN_ONLY));
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
        final var result = new DefaultComesLast().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(2);
        assertThat(result.diagnostics()).hasSize(1);
    }
}
