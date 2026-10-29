package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Annotations render from their own source text rather than javac's pretty-printer. The printer
 * reconstructs an annotation from the parse tree, which silently discards anything not represented
 * there — a trailing comma the lint pass just added, or a comment inside the argument list.
 */
class AnnotationRenderingTest {

    @Test
    void annotationArrayKeepsTheAuthorsPunctuation() {
        // ArrayTrailingComma deliberately skips annotation arrays. Were it to fire, the comma would
        // now survive into the output rather than being normalized away by the pretty-printer.
        final var source = """
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;

                @Target({ElementType.LOCAL_VARIABLE, ElementType.PARAMETER})
                @interface Fixture {}
                """;
        assertThat(Javafmt.format(source)).contains("{ElementType.LOCAL_VARIABLE, ElementType.PARAMETER}");
    }

    @Test
    void commentInsideAnAnnotationArgumentListIsKept() {
        final var source = """
                @SuppressWarnings(/* keep me */ "unchecked")
                class Fixture {}
                """;
        assertThat(Javafmt.format(source)).contains("/* keep me */");
    }

    @Test
    void ownLineAnnotationKeepsACommentFromItsSource() {
        // The own-line path is a separate renderer from the inline one, and had the same toString
        // problem.
        final var source = """
                class Fixture {

                    @SuppressWarnings(/* keep me */ "unchecked")
                    void method() {}
                }
                """;
        assertThat(Javafmt.format(source)).contains("/* keep me */");
    }

    @Test
    void formattingIsStillIdempotent() {
        final var source = """
                @SuppressWarnings(/* keep me */ "unchecked")
                class Fixture {}
                """;
        final var once = Javafmt.format(source);
        assertThat(Javafmt.format(once)).isEqualTo(once);
    }
}
