package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class ArrayTrailingCommaTest {

    @Test
    void singleElementArrayGetsTrailingComma() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int[] arr = {1};
            }
            """);
        final var result = new ArrayTrailingComma().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo(",");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void multiElementArrayGetsTrailingCommaAfterLastElement() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int[] arr = {1, 2, 3};
            }
            """);
        final var result = new ArrayTrailingComma().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo(",");
    }

    @Test
    void existingTrailingCommaProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int[] arr = {1, 2, 3,};
            }
            """);
        final var result = new ArrayTrailingComma().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void emptyInitializerProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int[] arr = {};
            }
            """);
        final var result = new ArrayTrailingComma().apply(unit);
        assertThat(result.edits()).isEmpty();
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
        final var result = new ArrayTrailingComma().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void nestedArrayInitializersEachGetTrailingComma() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                int[][] arr = {{1, 2}, {3, 4}};
            }
            """);
        final var result = new ArrayTrailingComma().apply(unit);
        // outer array + two inner arrays, none have trailing commas
        assertThat(result.edits()).hasSize(3);
        assertThat(result.edits()).allSatisfy(e -> assertThat(e.replacement()).isEqualTo(","));
    }

    @Test
    void annotationArrayGetsTrailingComma() {
        final var unit = JavaParser.parseUnit("""
            @SuppressWarnings({"foo", "bar"})
            class Fixture {}
            """);
        final var result = new ArrayTrailingComma().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo(",");
    }
}
