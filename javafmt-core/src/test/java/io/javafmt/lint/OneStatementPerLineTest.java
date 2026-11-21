package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        final var result = LintFixtures.apply(new OneStatementPerLine(), unit.source());
        assertThat(result.edits()).hasSize(1);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(LintFixtures.fix(new OneStatementPerLine(), unit.source()))
                .isEqualTo("class Fixture {\n\n    void test() {\n        int a = 1;\n        int b = 2;\n    }\n}\n");
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
        assertThat(LintFixtures.apply(new OneStatementPerLine(), unit.source()).edits())
                .isEmpty();
        assertThat(LintFixtures.fix(new OneStatementPerLine(), unit.source())).isEqualTo(unit.source());
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
        assertThat(LintFixtures.apply(new OneStatementPerLine(), unit.source()).edits())
                .hasSize(2);
        assertThat(LintFixtures.fix(new OneStatementPerLine(), unit.source()))
                .isEqualTo("""
                           class Fixture {

                               void test() {
                                   doA();
                                   doB();
                                   doC();
                               }
                               native void doA();
                               native void doB();
                               native void doC();
                           }
                           """);
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
        assertThat(LintFixtures.apply(new OneStatementPerLine(), unit.source()).edits())
                .isEmpty();
        assertThat(LintFixtures.fix(new OneStatementPerLine(), unit.source())).isEqualTo(unit.source());
    }
}
