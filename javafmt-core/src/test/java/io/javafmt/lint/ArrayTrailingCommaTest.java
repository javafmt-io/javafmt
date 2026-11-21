package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArrayTrailingCommaTest {

    @Test
    void singleElementArrayGetsTrailingComma() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {1};
            }
            """);
        final var result = LintFixtures.apply(new ArrayTrailingComma(), unit.source());
        assertThat(result.edits()).hasSize(1);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source())).isEqualTo("class Fixture {\n\n    int[] arr = {1,};\n}\n");
    }

    @Test
    void multiElementArrayGetsTrailingCommaAfterLastElement() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {1, 2, 3};
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTrailingComma(), unit.source()).edits())
                .hasSize(1);
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source())).isEqualTo("class Fixture {\n\n    int[] arr = {1, 2, 3,};\n}\n");
    }

    @Test
    void existingTrailingCommaProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {1, 2, 3,};
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTrailingComma(), unit.source()).edits())
                .isEmpty();
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source())).isEqualTo(unit.source());
    }

    @Test
    void emptyInitializerProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {};
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTrailingComma(), unit.source()).edits())
                .isEmpty();
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source())).isEqualTo(unit.source());
    }

    @Test
    void dimensionOnlyArrayProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int[] arr = new int[5];
                }
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTrailingComma(), unit.source()).edits())
                .isEmpty();
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source())).isEqualTo(unit.source());
    }

    @Test
    void nestedArrayInitializersEachGetTrailingComma() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[][] arr = {{1, 2}, {3, 4}};
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTrailingComma(), unit.source()).edits())
                .hasSize(3);
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source()))
                .isEqualTo("class Fixture {\n\n    int[][] arr = {{1, 2,}, {3, 4,},};\n}\n");
    }

    @Test
    void annotationArrayGetsATrailingCommaToo() {
        final var unit = JavaParser.parseUnit("""
            @SuppressWarnings({"foo", "bar"})
            class Fixture {}
            """);
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source())).contains("{\"foo\", \"bar\",}");
    }

    @Test
    void anArrayOnARecordComponentOfARecordWithACompactConstructorGetsOneComma() {
        final var unit = JavaParser.parseUnit("""
            record Fixture(@Names({"a", "b"}) String name) {

                Fixture {}
            }
            """);
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source()))
                .contains("{\"a\", \"b\",}")
                .doesNotContain(",,");
    }

    @Test
    void aCommaInsideATrailingCommentIsNotTheArraysTrailingComma() {
        // java/lang/CharacterDataLatin1.java annotates its last element with a comment full of
        // commas. Reading one of those as the array's own trailing comma left the source without
        // one while the renderer printed one anyway, and the file came back refused.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {
                    1,  // one
                    2   // two, three, and so on
                };
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTrailingComma(), unit.source()).edits())
                .hasSize(1);
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source()))
                .isEqualTo("class Fixture {\n\n    int[] arr = {\n        1,  // one\n        2,   // two, three, and so on\n    };\n}\n");
    }

    @Test
    void aCommaInsideABlockCommentIsNotTheArraysTrailingCommaEither() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {1, 2 /* a, b */};
            }
            """);
        assertThat(LintFixtures.apply(new ArrayTrailingComma(), unit.source()).edits())
                .hasSize(1);
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source()))
                .isEqualTo("class Fixture {\n\n    int[] arr = {1, 2 /* a, b */,};\n}\n");
    }

    @Test
    void theCommaGoesAfterEveryBlockCommentBesideTheLastElement() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {1, 2 /* x */ /* y */ // z
                };
            }
            """);
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source())).contains("2 /* x */ /* y */, // z");
    }

    @Test
    void theCommaStaysBeforeABlockCommentOnTheNextLine() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {1, 2
                    /* x */};
            }
            """);
        assertThat(LintFixtures.fix(new ArrayTrailingComma(), unit.source())).contains("2,\n");
    }
}
