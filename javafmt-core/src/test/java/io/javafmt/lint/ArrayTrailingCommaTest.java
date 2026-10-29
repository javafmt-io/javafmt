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
        final var result = new ArrayTrailingComma().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo(",");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void multiElementArrayGetsTrailingCommaAfterLastElement() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {1, 2, 3};
            }
            """);
        final var result = new ArrayTrailingComma().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo(",");
    }

    @Test
    void existingTrailingCommaProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {1, 2, 3,};
            }
            """);
        final var result = new ArrayTrailingComma().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void emptyInitializerProducesNoEdit() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {};
            }
            """);
        final var result = new ArrayTrailingComma().apply(LintContext.parseOnly(unit));
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
        final var result = new ArrayTrailingComma().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void nestedArrayInitializersEachGetTrailingComma() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[][] arr = {{1, 2}, {3, 4}};
            }
            """);
        final var result = new ArrayTrailingComma().apply(LintContext.parseOnly(unit));
        // outer array + two inner arrays, none have trailing commas
        assertThat(result.edits()).hasSize(3);
        assertThat(result.edits()).allSatisfy(e -> assertThat(e.replacement()).isEqualTo(","));
    }

    @Test
    void annotationArrayIsLeftAlone() {
        // Annotation arguments are almost always one line, where a trailing comma reads as a typo
        // rather than as diff hygiene. Annotations also render from source now, so a comma added
        // here would survive into the output instead of being normalized away.
        final var unit = JavaParser.parseUnit("""
            @SuppressWarnings({"foo", "bar"})
            class Fixture {}
            """);
        final var result = new ArrayTrailingComma().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
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
        final var result = new ArrayTrailingComma().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(LintEngine.applyEdits(unit.source(), result.edits())).contains("2,   // two, three, and so on");
    }

    @Test
    void aCommaInsideABlockCommentIsNotTheArraysTrailingCommaEither() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int[] arr = {1, 2 /* a, b */};
            }
            """);
        final var result = new ArrayTrailingComma().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(LintEngine.applyEdits(unit.source(), result.edits())).contains("1, 2, /* a, b */");
    }
}
