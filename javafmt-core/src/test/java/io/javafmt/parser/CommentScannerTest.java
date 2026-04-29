package io.javafmt.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CommentScannerTest {

    private static List<String> texts(final String source) {
        return CommentScanner.scan(source).stream().map(CommentToken::text).toList();
    }

    @Nested
    class StringAndCharLiterals {

        @ParameterizedTest(name = "{1}")
        @MethodSource("literalsHidingSlashes")
        void scannerIgnoresCommentSyntaxInsideLiterals(final String source, final String label) {
            assertThat(CommentScanner.scan(source)).as(label).isEmpty();
        }

        static Stream<Arguments> literalsHidingSlashes() {
            return Stream.of(
                Arguments.of("class C { String s = \"a\\\"/* not a comment */\"; }",
                    "escaped quote inside string keeps scanner inside the literal"),
                Arguments.of("class C { char c = '\\''; String s = \"// not a comment\"; }",
                    "char literal '\\'' followed by string with // sequence"),
                Arguments.of("class C { String s = \"\"\"\n  contains \\\"\"\" inside\n  /* still in block */\n\"\"\"; }",
                    "text block with escaped triple-quote and embedded /* */ sequences"),
                Arguments.of("class C { String s = \"contains /* and */ marks\"; }",
                    "string containing both /* and */ sequences"));
        }
    }

    @Nested
    class EofEdgeCases {

        @Test
        void lineCommentAtEofWithoutTrailingNewlineIsCaptured() {
            assertThat(texts("class C {} // trailing"))
                .singleElement()
                .isEqualTo("// trailing");
        }

        @Test
        void unterminatedBlockCommentAtEofIsCapturedToEnd() {
            final var source = "class C {} /* unterminated";
            final var tokens = CommentScanner.scan(source);

            assertThat(tokens).singleElement().satisfies(t -> {
                assertThat(t.text()).startsWith("/* unterminated");
                assertThat(t.end()).isEqualTo(source.length());
            });
        }
    }

    @Nested
    class AdjacentSequences {

        @Test
        void lineCommentImmediatelyFollowedByJavadocAreScannedAsTwoTokens() {
            final var source = """
                // first
                /** second */
                class C {}
                """;

            assertThat(texts(source)).containsExactly("// first", "/** second */");
        }

        @Test
        void unicodeEscapeForSlashIsTreatedAsLiteralCharsByScanner() {
            // The scanner is byte-level: `/` is six chars, not a `/`.
            // So the sequence `// comment` is not seen as `// comment`.
            final var source = "class C { String s = \"\\u002f/ not a comment\"; }";
            assertThat(CommentScanner.scan(source)).isEmpty();
        }

        @Test
        void slashSlashStartingALineEndsAtCarriageReturn() {
            final var source = "class C {} // crlf\r\nclass D {}";
            assertThat(texts(source)).singleElement().isEqualTo("// crlf");
        }
    }
}
