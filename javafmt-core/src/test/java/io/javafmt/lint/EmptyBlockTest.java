package io.javafmt.lint;

import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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
        final var result = new EmptyBlock().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst()).isInstanceOf(Diagnostic.Warning.class);
        assertThat(result.diagnostics().getFirst().message()).containsIgnoringCase("catch");
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
        final var result = new EmptyBlock().apply(LintContext.parseOnly(unit));
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
        final var result = new EmptyBlock().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst().message()).containsIgnoringCase("finally");
    }

    @Test
    void emptyConstructorProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                Fixture() {
                }
            }
            """);
        final var result = new EmptyBlock().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void anEmptyMethodBodyProducesNoWarning() {
        // A no-op method is a design, not an oversight. Running javafmt over its own source warned
        // six times and every one was deliberate — a no-op cache and four AuditListener callbacks.
        // Checkstyle's EmptyBlock has no token for a method body; this warning was javafmt's own.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void doNothing() {
                }
            }
            """);
        final var result = new EmptyBlock().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).isEmpty();
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
        final var result = new EmptyBlock().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).isEmpty();
    }

    static Stream<String> blocksHoldingOnlyAComment() {
        return Stream.of("{ /* c */ }", "{ // c\n    }", "{\n        // c\n    }")
                .flatMap(block -> Stream.of(
                        "static " + block,
                        "void m() { try { init(); } catch (RuntimeException e) " + block + " }",
                        "void m() { try { init(); } finally " + block + " }",
                        "void m() { synchronized (this) " + block + " }"));
    }

    @ParameterizedTest
    @MethodSource("blocksHoldingOnlyAComment")
    void aBlockHoldingOnlyACommentDoesNotWarn(final String member) {
        final var unit = JavaParser.parseUnit("class Fixture {\n    " + member + "\n    static void init() {}\n}\n");
        assertThat(new EmptyBlock().apply(LintContext.parseOnly(unit)).diagnostics())
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"static {}|1", "{}|0", "static { init(); }|0"})
    void onlyAnEmptyStaticInitializerWarns(final String initializer, final int warnings) {
        final var unit = JavaParser.parseUnit("class Fixture {\n    " + initializer + "\n    static void init() {}\n}\n");
        final var result = new EmptyBlock().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).hasSize(warnings);
        result.diagnostics().forEach(d -> assertThat(d.message()).isEqualTo("empty static initializer"));
    }
}
