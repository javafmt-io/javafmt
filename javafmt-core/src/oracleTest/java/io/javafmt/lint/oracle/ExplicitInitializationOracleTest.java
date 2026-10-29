package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.ExplicitInitialization}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * {@code ExplicitInitializationCheck}.
 *
 * <p>Both sides scope the check to fields and both leave {@code final} and interface fields
 * alone, since those have no later assignment to fall back on. One case is out of the corpus
 * because javafmt does not match Checkstyle on it: {@code char c = '\0';} is a character literal
 * rather than the integer zero the rule looks for, so javafmt keeps the initializer.
 */
class ExplicitInitializationOracleTest {

    ExplicitInitializationOracleTest() {}

    private static final String CORPUS = "explicit-initialization";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.coding.ExplicitInitializationCheck";

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
