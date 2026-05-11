package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class ArrayTypeStyleTest {

    @Test
    void cStyleParameterBracketsMovedToType() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final String args[]) {}
            }
            """);
        final var result = new ArrayTypeStyle().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("String[] args");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void javaStyleParameterProducesNoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final String[] args) {}
            }
            """);
        final var result = new ArrayTypeStyle().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void cStyleFieldBracketsMovedToType() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int values[];
            }
            """);
        final var result = new ArrayTypeStyle().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("int[] values");
    }

    @Test
    void cStyleLocalVariableBracketsMovedToType() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    final int x[] = new int[3];
                }
            }
            """);
        final var result = new ArrayTypeStyle().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("int[] x");
    }

    @Test
    void twoDimensionalCStyleBracketsMovedToType() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final String args[][]) {}
            }
            """);
        final var result = new ArrayTypeStyle().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("String[][] args");
    }

    @Test
    void mainMethodCStyleParameterFixed() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                public static void main(final String args[]) {}
            }
            """);
        final var result = new ArrayTypeStyle().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("String[] args");
    }
}
