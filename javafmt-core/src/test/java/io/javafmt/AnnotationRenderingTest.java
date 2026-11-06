package io.javafmt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Annotations render through the Doc pipeline rather than javac's pretty-printer. The printer
 * reconstructs an annotation from the parse tree and silently discards anything not represented
 * there — the author's trailing comma, or a comment inside the argument list.
 */
class AnnotationRenderingTest {

    @Test
    void annotationArrayKeepsTheAuthorsPunctuation() {
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
