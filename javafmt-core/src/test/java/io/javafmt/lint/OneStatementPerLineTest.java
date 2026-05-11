package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class OneStatementPerLineTest {

    @Test
    void twoStatementsOnSameLineGetSplit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    int a = 1; int b = 2;
                }
            }
            """);
        final var result = new OneStatementPerLine().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).startsWith("\n");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void statementsOnDifferentLinesProduceNoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    int a = 1;
                    int b = 2;
                }
            }
            """);
        final var result = new OneStatementPerLine().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void threeStatementsOnSameLineEmitsTwoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    doA(); doB(); doC();
                }
                native void doA();
                native void doB();
                native void doC();
            }
            """);
        final var result = new OneStatementPerLine().apply(unit);
        assertThat(result.edits()).hasSize(2);
    }

    @Test
    void singleStatementPerLineBlockProducesNoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    doA();
                    doB();
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new OneStatementPerLine().apply(unit);
        assertThat(result.edits()).isEmpty();
    }
}
