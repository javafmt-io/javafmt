package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class MultipleVariableDeclarationsTest {

    @Test
    void twoVariablesOnSameLineAreSplit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    int a, b;
                }
            }
            """);
        final var result = new MultipleVariableDeclarations().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.diagnostics()).isEmpty();
        final var replacement = result.edits().get(0).replacement();
        assertThat(replacement).contains("int a;");
        assertThat(replacement).contains("int b;");
    }

    @Test
    void threeVariablesAreSplit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    int a, b, c;
                }
            }
            """);
        final var result = new MultipleVariableDeclarations().apply(unit);
        assertThat(result.edits()).hasSize(1);
        final var replacement = result.edits().get(0).replacement();
        assertThat(replacement).contains("int a;");
        assertThat(replacement).contains("int b;");
        assertThat(replacement).contains("int c;");
    }

    @Test
    void initializerPreservedWhenSplitting() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    int a, b = 5;
                }
            }
            """);
        final var result = new MultipleVariableDeclarations().apply(unit);
        assertThat(result.edits()).hasSize(1);
        final var replacement = result.edits().get(0).replacement();
        assertThat(replacement).contains("int a;");
        assertThat(replacement).contains("int b = 5;");
    }

    @Test
    void separateDeclarationsProduceNoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    int a;
                    int b;
                }
            }
            """);
        final var result = new MultipleVariableDeclarations().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void fieldMultipleDeclarationsSplit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int x, y;
            }
            """);
        final var result = new MultipleVariableDeclarations().apply(unit);
        assertThat(result.edits()).hasSize(1);
        final var replacement = result.edits().get(0).replacement();
        assertThat(replacement).contains("int x;");
        assertThat(replacement).contains("int y;");
    }
}
