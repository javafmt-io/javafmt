package io.javafmt.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TokenScannerTest {

    private static List<String> texts(final String source) {
        return TokenScanner.scan(source).stream().map(SourceToken::text).toList();
    }

    @Test
    void splitsIdentifiersKeywordsAndPunctuation() {
        assertThat(texts("class A {}")).containsExactly("class", "A", "{", "}");
    }

    @Test
    void skipsWhitespaceEntirely() {
        assertThat(texts("int   x\n\t=\r\n 1 ;")).containsExactly("int", "x", "=", "1", ";");
    }

    @Test
    void indentationChangesDoNotChangeTheTokenStream() {
        assertThat(texts("class A {\nint x;\n}")).isEqualTo(texts("class A {\n        int x;\n}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"// gone", "/* gone */", "/** gone */", "/* multi\n * line\n */"})
    void skipsComments(final String comment) {
        assertThat(texts("int x; " + comment + "\nint y;")).containsExactly("int", "x", ";", "int", "y", ";");
    }

    @Test
    void keepsStringLiteralsWhole() {
        assertThat(texts("s = \"a b // c /* d */\";")).containsExactly("s", "=", "\"a b // c /* d */\"", ";");
    }

    @Test
    void keepsEscapedQuotesInsideStringLiterals() {
        assertThat(texts("s = \"a\\\"b\";")).containsExactly("s", "=", "\"a\\\"b\"", ";");
    }

    @Test
    void keepsCharLiteralsWhole() {
        assertThat(texts("c = '\"'; d = '\\'';")).containsExactly("c", "=", "'\"'", ";", "d", "=", "'\\''", ";");
    }

    @Test
    void keepsTextBlocksWhole() {
        final var source = "s = \"\"\"\n    hi \"quoted\"\n    \"\"\";";
        assertThat(texts(source)).containsExactly("s", "=", "\"\"\"\n    hi \"quoted\"\n    \"\"\"", ";");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "42", "1_000L", "0x1FL", "0b1010", "3.14", "3.14f", "1e-5", "0x1p-3", ".5f", "0777"})
    void keepsNumericLiteralsWhole(final String literal) {
        assertThat(texts("x = " + literal + ";")).containsExactly("x", "=", literal, ";");
    }

    @Test
    void doesNotGlueAMemberSelectOntoAPrecedingNumber() {
        assertThat(texts("x = 1; y.z();")).containsExactly("x", "=", "1", ";", "y", ".", "z", "(", ")", ";");
    }

    @Test
    void lexesMultiCharacterOperatorsWholeWithMaximalMunch() {
        // Per-character marks used to be enough "because whitespace is skipped anyway", which is
        // exactly what made `- -x` and `--x` the same token stream and let the renderer turn a
        // double negation into a decrement unnoticed.
        assertThat(texts("a -> b")).containsExactly("a", "->", "b");
        assertThat(texts("x >>>= 1")).containsExactly("x", ">>>=", "1");
        assertThat(texts("i++ + ++j")).containsExactly("i", "++", "+", "++", "j");
    }

    @Test
    void doubleNegationIsNotADecrement() {
        assertThat(texts("- -x")).containsExactly("-", "-", "x");
        assertThat(texts("--x")).containsExactly("--", "x");
    }

    @Test
    void nestedGenericsStillClose() {
        // javac splits `>>` itself so that List<List<String>> parses; a lexer that joined them
        // here would disagree with the only reader that matters.
        assertThat(texts("List<List<String>> xs")).containsExactly("List", "<", "List", "<", "String", ">", ">", "xs");
    }

    @Test
    void recordsSourceOffsetsForEachToken() {
        final var tokens = TokenScanner.scan("int  x;");
        assertThat(tokens).extracting(SourceToken::start).containsExactly(0, 5, 6);
        assertThat(tokens).extracting(SourceToken::end).containsExactly(3, 6, 7);
    }

    @Test
    void emptySourceHasNoTokens() {
        assertThat(TokenScanner.scan("")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"'class A {}', 4", "'', 0", "'// only a comment', 0"})
    void tokenCount(final String source, final int expected) {
        assertThat(TokenScanner.scan(source)).hasSize(expected);
    }

    @Test
    void unicodeIdentifiersAreOneToken() {
        assertThat(texts("int café = 1;")).containsExactly("int", "café", "=", "1", ";");
    }

    @Test
    void aMinusAfterAHexLiteralIsAnOperatorNotAnExponentSign() {
        // In a hex literal `E` is a digit, not an exponent marker — the marker there is `p`.
        // Reading `0x7E-ch` as one token made javafmt refuse java/util/regex/ASCII.java, a file it
        // had in fact formatted correctly.
        assertThat(texts("x = 0x7E-ch;")).containsExactly("x", "=", "0x7E", "-", "ch", ";");
    }

    @Test
    void aSignAfterARealExponentIsStillPartOfTheLiteral() {
        assertThat(texts("y = 1e-5;")).containsExactly("y", "=", "1e-5", ";");
        assertThat(texts("z = 0x1p-3;")).containsExactly("z", "=", "0x1p-3", ";");
    }
}
