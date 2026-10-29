package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.OneStatementPerLine}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * {@code OneStatementPerLineCheck}.
 *
 * <p>Both javafmt and Checkstyle exempt {@code for} headers, whose comma-separated init and
 * update clauses are one statement each by the grammar.
 */
class OneStatementPerLineOracleTest {

    OneStatementPerLineOracleTest() {}

    private static final String CORPUS = "one-statement-per-line";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.coding.OneStatementPerLineCheck";

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
