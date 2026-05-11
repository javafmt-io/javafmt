package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class EmptyStatementTest {

    @Test
    void strayStandaloneEmptyStatementInBlockIsDeleted() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    ;
                    doSomething();
                }
                native void doSomething();
            }
            """);
        final var result = new EmptyStatement().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void multipleEmptyStatementsAllDeleted() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    ;
                    ;
                }
            }
            """);
        final var result = new EmptyStatement().apply(unit);
        assertThat(result.edits()).hasSize(2);
    }

    @Test
    void blockWithNoEmptyStatementsProducesNoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    doSomething();
                }
                native void doSomething();
            }
            """);
        final var result = new EmptyStatement().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void infiniteForLoopProducesNoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    for (;;) {
                        break;
                    }
                }
            }
            """);
        final var result = new EmptyStatement().apply(unit);
        assertThat(result.edits()).isEmpty();
    }
}
