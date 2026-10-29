package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.NewlineAtEndOfFile}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * {@code NewlineAtEndOfFileCheck}.
 *
 * <p>Unlike the other oracles this one is a file-level check, so {@code CheckstyleOracle} hangs
 * it off the {@code Checker} rather than the {@code TreeWalker}. Only a missing terminator is a
 * Checkstyle violation; {@code corpus/newline-at-end-of-file/missing-newline.java} therefore has
 * no trailing newline on purpose, and {@link #corpusContainsViolationsToBegin} fails loudly if an
 * editor ever adds one back.
 */
class NewlineAtEndOfFileOracleTest {

    NewlineAtEndOfFileOracleTest() {}

    private static final String CORPUS = "newline-at-end-of-file";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.NewlineAtEndOfFileCheck";

    private static final Map<String, String> PROPERTIES = Map.of();

    @ParameterizedTest(name = "{0}")
    @MethodSource(value = "corpus")
    void javafmtOutputIsCheckstyleClean(final String name, final String source) {
        OracleCorpus.assertJavafmtMakesItClean(name, source, CHECK, PROPERTIES);
    }

    @Test
    void corpusContainsViolationsToBegin() {
        OracleCorpus.assertCorpusExercisesRule(CORPUS, CHECK, PROPERTIES);
    }

    static Stream<Arguments> corpus() {
        return OracleCorpus.load(CORPUS);
    }
}
