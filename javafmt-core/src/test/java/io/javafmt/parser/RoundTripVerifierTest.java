package io.javafmt.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoundTripVerifierTest {

    private static final String SOURCE = """
            class Fixture {

                // a leading comment
                int compute(int x) {
                    return x + 1; /* tail */
                }
            }
            """;

    @Test
    void reindentedOutputVerifies() {
        final var reindented = SOURCE.replace("    int compute", "        int compute");
        assertThat(RoundTripVerifier.verify(SOURCE, reindented)).isEmpty();
    }

    @Test
    void identicalSourceVerifies() {
        assertThat(RoundTripVerifier.verify(SOURCE, SOURCE)).isEmpty();
    }

    @Test
    void aDeletedTokenIsReported() {
        assertThat(RoundTripVerifier.verify(SOURCE, SOURCE.replace("x + 1", "x")))
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("token");
    }

    @Test
    void aDeletedTokenReportIdentifiesTheDroppedText() {
        assertThat(RoundTripVerifier.verify("case Integer i when i > 0 -> 1;", "case Integer i -> 1;"))
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("when");
    }

    @Test
    void anAddedTokenIsReported() {
        assertThat(RoundTripVerifier.verify(SOURCE, SOURCE.replace("x + 1", "x + 1 + 2"))).isPresent();
    }

    @Test
    void aChangedTokenIsReported() {
        assertThat(RoundTripVerifier.verify(SOURCE, SOURCE.replace("x + 1", "x + 2"))).isPresent();
    }

    @Test
    void aDeletedCommentIsReported() {
        assertThat(RoundTripVerifier.verify(SOURCE, SOURCE.replace("/* tail */", "")))
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("comment");
    }

    @Test
    void aReindentedBlockCommentVerifies() {
        final var before = "class A {\n    /* one\n       two */\n    int x;\n}\n";
        final var after = "class A {\n        /* one\n           two */\n        int x;\n}\n";
        assertThat(RoundTripVerifier.verify(before, after)).isEmpty();
    }

    @Test
    void aReindentedTextBlockVerifies() {
        final var before = "class A {\n    String s = \"\"\"\n            hi\n            \"\"\";\n}\n";
        final var after = "class A {\n    String s = \"\"\"\n                    hi\n                    \"\"\";\n}\n";
        assertThat(RoundTripVerifier.verify(before, after)).isEmpty();
    }

    @Test
    void aTextBlockWithAlteredContentIsReported() {
        final var before = "class A {\n    String s = \"\"\"\n            hi\n            \"\"\";\n}\n";
        final var after = "class A {\n    String s = \"\"\"\n            bye\n            \"\"\";\n}\n";
        assertThat(RoundTripVerifier.verify(before, after)).isPresent();
    }

    @Test
    void reorderedMembersAreReported() {
        // Tokens are compared in order now. Every reorder javafmt performs is a lint edit, so it is
        // already in the source this runs against; a permutation appearing here is the renderer
        // moving code on its own.
        final var before = "class A {\n    void a() {}\n\n    void b() {}\n}\n";
        final var after = "class A {\n    void b() {}\n\n    void a() {}\n}\n";
        assertThat(RoundTripVerifier.verify(before, after)).isPresent();
    }

    @Test
    void aMovedTokenIsReportedWithItsPosition() {
        // `new Annotation[numParameters][]` printed as `new Annotation[][numParameters]` on
        // java.base, and the output did not compile. Every token is still there; one has moved, so
        // a multiset saw nothing.
        final var before = "class A {\n    Object v(int n) {\n        return new int[n][];\n    }\n}\n";
        final var after = "class A {\n    Object v(int n) {\n        return new int[][n];\n    }\n}\n";
        assertThat(RoundTripVerifier.verify(before, after))
                .hasValueSatisfying(message -> assertThat(message).contains("diverges at token"));
        assertThat(RoundTripVerifier.firstDifferingOffset(before, after)).isPresent();
    }

    @Test
    void anUnchangedFileHasNoDivergingOffset() {
        final var source = "class A {\n    int x = 1;\n}\n";
        assertThat(RoundTripVerifier.firstDifferingOffset(source, source)).isEmpty();
    }

    @Test
    void aDoubleNegationTurnedIntoADecrementIsReported() {
        // Two `-` marks and one `--` were the same token stream while operators lexed a character
        // at a time, which is how the renderer printed `- -x` as `--x` unnoticed. Maximal munch
        // makes them different multisets, so this is caught without comparing sequences.
        final var before = "class A {\n    int v(int x) {\n        return - -x;\n    }\n}\n";
        final var after = "class A {\n    int v(int x) {\n        return --x;\n    }\n}\n";
        assertThat(RoundTripVerifier.verify(before, after)).isPresent();
    }

    @Test
    void anIncrementTurnedIntoTwoPlusSignsIsReported() {
        final var before = "class A {\n    int v(int x) {\n        return ++x;\n    }\n}\n";
        final var after = "class A {\n    int v(int x) {\n        return + +x;\n    }\n}\n";
        assertThat(RoundTripVerifier.verify(before, after)).isPresent();
    }

    @Test
    void sortedImportsVerify() {
        final var before = "import java.util.Set;\nimport java.util.List;\n\nclass A {}\n";
        final var after = "import java.util.List;\nimport java.util.Set;\n\nclass A {}\n";
        assertThat(RoundTripVerifier.verify(before, after)).isEmpty();
    }

    @Test
    void deletionIsStillCaughtDespiteOrderInsensitivity() {
        final var before = "class A {\n    void a() {}\n\n    void b() {}\n}\n";
        final var after = "class A {\n    void b() {}\n}\n";
        assertThat(RoundTripVerifier.verify(before, after)).isPresent();
    }
}
