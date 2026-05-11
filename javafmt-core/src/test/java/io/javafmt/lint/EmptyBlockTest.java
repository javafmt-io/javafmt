package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class EmptyBlockTest {

    @Test
    void emptyCatchBlockProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    try {
                        doSomething();
                    } catch (final Exception e) {
                    }
                }
                native void doSomething();
            }
            """);
        final var result = new EmptyBlock().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0)).isInstanceOf(Diagnostic.Warning.class);
        assertThat(result.diagnostics().get(0).message()).containsIgnoringCase("catch");
    }

    @Test
    void emptyCatchWithCommentProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    try {
                        doSomething();
                    } catch (final Exception e) {
                        // intentionally empty
                    }
                }
                native void doSomething();
            }
            """);
        final var result = new EmptyBlock().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void emptyFinallyBlockProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    try {
                        doSomething();
                    } finally {
                    }
                }
                native void doSomething();
            }
            """);
        final var result = new EmptyBlock().apply(unit);
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0).message()).containsIgnoringCase("finally");
    }

    @Test
    void emptyConstructorProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                Fixture() {
                }
            }
            """);
        final var result = new EmptyBlock().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void emptyMethodBodyProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void doNothing() {
                }
            }
            """);
        final var result = new EmptyBlock().apply(unit);
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0).message()).containsIgnoringCase("method");
    }

    @Test
    void nonEmptyBlockProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    try {
                        doSomething();
                    } catch (final Exception e) {
                        handle(e);
                    }
                }
                native void doSomething();
                native void handle(Exception e);
            }
            """);
        final var result = new EmptyBlock().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }
}
