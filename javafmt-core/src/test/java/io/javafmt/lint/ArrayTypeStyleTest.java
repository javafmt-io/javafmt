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
        final var result = new ArrayTypeStyle().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("String[] args");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void javaStyleParameterProducesNoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test(final String[] args) {}
            }
            """);
        final var result = new ArrayTypeStyle().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void cStyleFieldBracketsMovedToType() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int values[];
            }
            """);
        final var result = new ArrayTypeStyle().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("int[] values");
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
        final var result = new ArrayTypeStyle().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("int[] x");
    }

    @Test
    void twoDimensionalCStyleBracketsMovedToType() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test(final String args[][]) {}
            }
            """);
        final var result = new ArrayTypeStyle().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("String[][] args");
    }

    @Test
    void mainMethodCStyleParameterFixed() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                public static void main(final String args[]) {}
            }
            """);
        final var result = new ArrayTypeStyle().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("String[] args");
    }

    @Test
    void multiDeclaratorGroupIsLeftToMultipleVariableDeclarations() {
        // The C-style type span covers the whole group ("int a, b[]"), so rewriting it here would
        // destroy the other declarators. MultipleVariableDeclarations splits the group first and
        // this rule normalizes the resulting single declarations on the next iteration.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int a, b[];
                }
            }
            """);
        final var result = new ArrayTypeStyle().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void aCStyleReturnTypeIsRewrittenByTheRule() {
        // Not just by the renderer: the renderer printing the Java-style form on its own looks, to
        // the output verifier, exactly like the renderer moving code around.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int first()[] {
                    return null;
                }
            }
            """);
        final var result = new ArrayTypeStyle().apply(LintContext.parseOnly(unit));
        assertThat(LintEngine.applyEdits(unit.source(), result.edits())).contains("int[] first()");
    }

    @Test
    void aMultiDimensionalCStyleReturnTypeIsRewritten() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                static String names(final int n)[][] {
                    return null;
                }
            }
            """);
        final var result = new ArrayTypeStyle().apply(LintContext.parseOnly(unit));
        assertThat(LintEngine.applyEdits(unit.source(), result.edits())).contains("static String[][] names(final int n)");
    }

    @Test
    void aJavaStyleReturnTypeProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] already() {
                    return null;
                }
            }
            """);
        assertThat(new ArrayTypeStyle().apply(LintContext.parseOnly(unit)).edits()).isEmpty();
    }
}
