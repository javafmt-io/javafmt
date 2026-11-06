package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmptyStatementTest {

    @Test
    void deletesOnlyTheStraySemicolonsBetweenClassMembers() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                ;
                int a = 1, b = 2;
                // a; comment
                String s = ";";
                ;
                class Inner {
                    ;
                    void f() {}
                }
                /* ; */ ;
            }
            """);
        final var result = new EmptyStatement().apply(LintContext.parseOnly(unit));
        assertThat(LintEngine.applyEdits(unit.source(), result.edits()))
                .isEqualTo("""
                           class Fixture {
                              \s
                               int a = 1, b = 2;
                               // a; comment
                               String s = ";";
                              \s
                               class Inner {
                                  \s
                                   void f() {}
                               }
                               /* ; */\s
                           }
                           """);
    }

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
        final var result = new EmptyStatement().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEmpty();
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
        final var result = new EmptyStatement().apply(LintContext.parseOnly(unit));
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
        final var result = new EmptyStatement().apply(LintContext.parseOnly(unit));
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
        final var result = new EmptyStatement().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "try (Resource first = open(); ) {}|try (Resource first = open() ) {}",
            "try (Resource first = open(); Resource second = open();) {}|try (Resource first = open(); Resource second = open()) {}",
            "try (held; ) {}|try (held ) {}",
            "try (held /* last */ ; ) {}|try (held /* last */  ) {}",
            "try (Resource first = open()) {}|try (Resource first = open()) {}",
            "try (held) {}|try (held) {}",
    })
    void deletesTheSemicolonAfterTheLastResource(final String statement, final String expected) {
        final var unit = JavaParser.parseUnit(
                "class Fixture {\n    void test(Resource held) throws Exception {\n        " + statement + "\n    }\n}\n");
        final var result = new EmptyStatement().apply(LintContext.parseOnly(unit));
        assertThat(LintEngine.applyEdits(unit.source(), result.edits()))
                .isEqualTo("class Fixture {\n    void test(Resource held) throws Exception {\n        " + expected + "\n    }\n}\n");
    }
}
