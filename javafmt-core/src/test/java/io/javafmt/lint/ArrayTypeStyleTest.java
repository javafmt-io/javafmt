package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ArrayTypeStyleTest {

    @Test
    void cStyleParameterBracketsMovedToType() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test(final String arguments[]) {}
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTypeStyle(), unit.source()).edits())
                .hasSize(1);
        assertThat(LintFixtures.apply(new ArrayTypeStyle(), unit.source()).diagnostics())
                .isEmpty();
        assertThat(LintFixtures.fix(new ArrayTypeStyle(), unit.source()))
                .isEqualTo("class Fixture {\n\n    void test(final String[] arguments) {}\n}\n");
    }

    @Test
    void javaStyleParameterProducesNoEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test(final String[] arguments) {}
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTypeStyle(), unit.source()).edits())
                .isEmpty();
        assertThat(LintFixtures.fix(new ArrayTypeStyle(), unit.source())).isEqualTo(unit.source());
    }

    @Test
    void cStyleFieldBracketsMovedToType() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int values[];
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTypeStyle(), unit.source()).edits())
                .hasSize(1);
        assertThat(LintFixtures.fix(new ArrayTypeStyle(), unit.source())).isEqualTo("class Fixture {\n\n    int[] values;\n}\n");
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
        assertThat(LintFixtures.apply(new ArrayTypeStyle(), unit.source()).edits())
                .hasSize(1);
        assertThat(LintFixtures.fix(new ArrayTypeStyle(), unit.source()))
                .isEqualTo("class Fixture {\n\n    void test() {\n        final int[] x = new int[3];\n    }\n}\n");
    }

    @Test
    void twoDimensionalCStyleBracketsMovedToType() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test(final String arguments[][]) {}
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTypeStyle(), unit.source()).edits())
                .hasSize(1);
        assertThat(LintFixtures.fix(new ArrayTypeStyle(), unit.source()))
                .isEqualTo("class Fixture {\n\n    void test(final String[][] arguments) {}\n}\n");
    }

    @Test
    void mainMethodCStyleParameterFixed() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                public static void main(final String arguments[]) {}
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTypeStyle(), unit.source()).edits())
                .hasSize(1);
        assertThat(LintFixtures.fix(new ArrayTypeStyle(), unit.source()))
                .isEqualTo("class Fixture {\n\n    public static void main(final String[] arguments) {}\n}\n");
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
        assertThat(LintFixtures.apply(new ArrayTypeStyle(), unit.source()).edits())
                .isEmpty();
        assertThat(LintFixtures.fix(new ArrayTypeStyle(), unit.source())).isEqualTo(unit.source());
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
        assertThat(LintFixtures.fix(new ArrayTypeStyle(), unit.source()))
                .isEqualTo("class Fixture {\n\n    int[] first() {\n        return null;\n    }\n}\n");
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
        assertThat(LintFixtures.fix(new ArrayTypeStyle(), unit.source()))
                .isEqualTo("class Fixture {\n\n    static String[][] names(final int n) {\n        return null;\n    }\n}\n");
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
        assertThat(LintFixtures.apply(new ArrayTypeStyle(), unit.source()).edits())
                .isEmpty();
        assertThat(LintFixtures.fix(new ArrayTypeStyle(), unit.source())).isEqualTo(unit.source());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "byte sub[/* size */];|byte[] sub /* size */;",
            "byte sub[]/* inner */[];|byte[][] sub /* inner */;",
            "byte sub /* array */ [];|byte[] sub /* array */;",
            "byte sub /* sub */ [];|byte[] sub /* sub */;",
    })
    void aCommentAmongTheCStyleBracketsMovesAfterTheName(final String declaration, final String expected) {
        final var unit = JavaParser.parseUnit("class Fixture {\n    " + declaration + "\n}\n");
        assertThat(LintFixtures.fix(new ArrayTypeStyle(), unit.source())).isEqualTo("class Fixture {\n    " + expected + "\n}\n");
    }
}
