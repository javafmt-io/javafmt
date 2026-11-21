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
        assertThat(LintFixtures.fix(new EmptyStatement(), unit.source()))
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
        final var result = LintFixtures.apply(new EmptyStatement(), unit.source());
        assertThat(result.edits()).hasSize(1);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(LintFixtures.fix(new EmptyStatement(), unit.source()))
                .isEqualTo("class Fixture {\n\n    void test() {\n        \n        doSomething();\n    }\n    native void doSomething();\n}\n");
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
        assertThat(LintFixtures.apply(new EmptyStatement(), unit.source()).edits())
                .hasSize(2);
        assertThat(LintFixtures.fix(new EmptyStatement(), unit.source()))
                .isEqualTo("class Fixture {\n\n    void test() {\n        \n        \n    }\n}\n");
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
        assertThat(LintFixtures.apply(new EmptyStatement(), unit.source()).edits())
                .isEmpty();
        assertThat(LintFixtures.fix(new EmptyStatement(), unit.source())).isEqualTo(unit.source());
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
        assertThat(LintFixtures.apply(new EmptyStatement(), unit.source()).edits())
                .isEmpty();
        assertThat(LintFixtures.fix(new EmptyStatement(), unit.source())).isEqualTo(unit.source());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "try (Resource first = open(); ) {}|try (Resource first = open() ) {}",
            "try (Resource first = open(); Resource second = open();) {}|try (Resource first = open(); Resource second = open()) {}",
            "try (held; ) {}|try (held ) {}",
            "try (held /* last */ ; ) {}|try (held /* last */  ) {}",
            "try (held /* a */ /* b */ ; ) {}|try (held /* a */ /* b */  ) {}",
            "try (Resource first = open()) {}|try (Resource first = open()) {}",
            "try (held) {}|try (held) {}",
    })
    void deletesTheSemicolonAfterTheLastResource(final String statement, final String expected) {
        final var unit = JavaParser.parseUnit(
                "class Fixture {\n    void test(Resource held) throws Exception {\n        " + statement + "\n    }\n}\n");
        assertThat(LintFixtures.fix(new EmptyStatement(), unit.source()))
                .isEqualTo("class Fixture {\n    void test(Resource held) throws Exception {\n        " + expected + "\n    }\n}\n");
    }

    @Test
    void aLoneCarriageReturnEndsTheLineCommentAfterTheLastResource() {
        final var source = "class Fixture {\r    void test(Resource held) throws Exception {\r        try (held // c\r) {}\n;\r    }\r}\r";
        final var result = LintFixtures.apply(new EmptyStatement(), source);
        assertThat(result.edits()).hasSize(1);
        assertThat(LintFixtures.fix(new EmptyStatement(), source))
                .isEqualTo("class Fixture {\r    void test(Resource held) throws Exception {\r        try (held // c\r) {}\n\r    }\r}\r");
    }
}
