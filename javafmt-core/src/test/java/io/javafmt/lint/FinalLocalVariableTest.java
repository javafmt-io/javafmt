package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

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
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("final ");
        assertThat(result.diagnostics()).isEmpty();
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
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
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
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
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
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
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
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
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
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void fieldNotTargetedByRule() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int x = 5;
            }
            """);
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
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
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
        // outer x and r are unreassigned in the enclosing block; lambda's x is a separate scope
        assertThat(result.edits()).hasSize(2);
        assertThat(result.edits()).allSatisfy(e -> assertThat(e.replacement()).isEqualTo("final "));
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
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
        // outer x and r are unreassigned in the enclosing block; inner class is a separate scope
        assertThat(result.edits()).hasSize(2);
        assertThat(result.edits()).allSatisfy(e -> assertThat(e.replacement()).isEqualTo("final "));
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
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
        // a and c are unreassigned; b is reassigned
        assertThat(result.edits()).hasSize(2);
        assertThat(result.edits()).allSatisfy(e -> assertThat(e.replacement()).isEqualTo("final "));
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
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
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
        final var result = new FinalLocalVariable().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }
}
