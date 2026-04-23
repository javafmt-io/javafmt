package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.parser.CommentScanner;
import io.github.jschneidereit.grind.parser.CommentToken;

import org.junit.jupiter.api.Test;

class CommentAuditTest {

    private static final String SOURCE = """
        package p;

        import java.util.List;
        import java.util.Map;

        /** class javadoc */
        class Fixture extends /* superclass note */ Parent implements /* iface1 */ A, /* iface2 */ B {
            <T /* type param */, U> void generic(T t, U u) {}

            void throwy() throws /* checked */ java.io.IOException, /* unchecked */ RuntimeException {}

            int x = 1 /* suffix */;

            int y = /* init expr */ 2 + 3;

            record Point(int a, /* between comps */ int b) {}

            void loopy() {
                for (int i = 0; /* cond */ i < 10; /* update */ i++) {
                    System.out.println(i);
                }
            }

            int[] arr = {1, /* mid */ 2, 3};

            void cast() {
                long n = (/* widen */ long) 5;
            }

            enum E {
                A(/* arg */ 1),
                B(/* arg2 */ 2);

                final int v;

                E(int v) {
                    this.v = v;
                }
            }

            void sw(String s) {
                switch (s) {
                    case /* maybe */ "a" -> {}
                    default -> {}
                }
            }
        }

        // trailing file comment
        """;

    @Test
    void everyCommentPositionIsPreserved() {
        final var before = CommentScanner.scan(SOURCE);
        final var formatted = Grind.format(SOURCE);
        final var afterTexts = CommentScanner.scan(formatted).stream()
            .map(CommentToken::text)
            .toList();
        final var missing = before.stream()
            .map(CommentToken::text)
            .filter(t -> !afterTexts.contains(t))
            .toList();
        assertThat(missing)
            .as("comments lost when formatting:%n%s", formatted)
            .isEmpty();
    }
}
