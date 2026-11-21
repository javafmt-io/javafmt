package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FinalLocalVariableTest {

    @Test
    void unreassignedLocalGetsFinalModifier() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int x = 5;
                }
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).hasSize(1);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(LintEngine.applyEdits(unit.source(), result.edits()))
                .isEqualTo("class Fixture {\n\n    void test() {\n        final int x = 5;\n    }\n}\n");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "@SuppressWarnings(\"unused\") String name = \"x\";|@SuppressWarnings(\"unused\") final String name",
            "@SuppressWarnings(\"unused\") var name = \"x\";|@SuppressWarnings(\"unused\") final var name",
    })
    void finalGoesAfterTheAnnotations(final String declaration, final String finalized) {
        final var unit = JavaParser.parseUnit("class Fixture { void test() { " + declaration + " } }");
        assertThat(LintFixtures.fix(new FinalLocalVariable(), unit.source())).contains(finalized);
    }

    @Test
    void reassignedLocalProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int x = 0;
                    x = 5;
                }
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void compoundAssignedLocalProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int x = 0;
                    x += 1;
                }
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void postfixIncrementProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int x = 0;
                    x++;
                }
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void prefixDecrementProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int x = 0;
                    --x;
                }
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void alreadyFinalLocalProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    final int x = 5;
                }
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void fieldNotTargetedByRule() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int x = 5;
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void localUnreassignedInOuterScopeGetsFinalDespiteLambdaShadow() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int x = 5;
                    Runnable r = () -> {
                        int x = 10;
                        x = 20;
                    };
                }
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).hasSize(2);
        assertThat(LintEngine.applyEdits(unit.source(), result.edits()))
                .isEqualTo("""
                           class Fixture {

                               void test() {
                                   final int x = 5;
                                   final Runnable r = () -> {
                                       int x = 10;
                                       x = 20;
                                   };
                               }
                           }
                           """);
    }

    @Test
    void localUnreassignedInOuterScopeGetsFinalDespiteAnonymousClassShadow() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int x = 5;
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            int x = 10;
                            x = 20;
                        }
                    };
                }
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).hasSize(2);
        assertThat(LintEngine.applyEdits(unit.source(), result.edits()))
                .isEqualTo("""
                           class Fixture {

                               void test() {
                                   final int x = 5;
                                   final Runnable r = new Runnable() {
                                       @Override
                                       public void run() {
                                           int x = 10;
                                           x = 20;
                                       }
                                   };
                               }
                           }
                           """);
    }

    @Test
    void multipleLocalsOnlyUnreassignedOnesGetFinal() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int a = 1;
                    int b = 2;
                    int c = 3;
                    b = 5;
                }
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).hasSize(2);
        assertThat(LintEngine.applyEdits(unit.source(), result.edits()))
                .isEqualTo("class Fixture {\n\n    void test() {\n        final int a = 1;\n        int b = 2;\n        final int c = 3;\n        b = 5;\n    }\n}\n");
    }

    @Test
    void unnamedVariableNotGivenFinalModifier() {
        // Java 22+: _ is an unnamed variable — final is neither useful nor necessary.
        final var unit = JavaParser.parseUnit("""
            import java.util.List;

            class Fixture {

                void test() {
                    for (var _ : List.of(1, 2, 3)) {
                        System.out.println("tick");
                    }
                }
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void multiDeclaratorGroupIsLeftToMultipleVariableDeclarations() {
        // Every declarator in `int a, b;` reports the same declaration start offset, so inserting
        // per declarator would stack up `final final int a, b;`. The group has to be split first.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int a, b;
                    System.out.println(a + b);
                }
            }
            """);
        final var result = LintFixtures.apply(new FinalLocalVariable(), unit.source());
        assertThat(result.edits()).isEmpty();
    }
}
