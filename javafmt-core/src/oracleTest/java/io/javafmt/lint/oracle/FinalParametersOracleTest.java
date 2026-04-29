package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.FinalParameters}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * equivalent {@code FinalParametersCheck}.
 *
 * <p>Checkstyle's defaults match javafmt's scope: {@code METHOD_DEF} and {@code CTOR_DEF},
 * with abstract/native/interface methods skipped automatically (the {@code final} keyword
 * is meaningless on parameters of methods that have no body). Divergences from this default
 * are configured here, with a comment explaining why.
 */
class FinalParametersOracleTest {

    private static final String CORPUS = "final-parameters";

    private static final String CHECK =
        "com.puppycrawl.tools.checkstyle.checks.FinalParametersCheck";

    private static final Map<String, String> PROPERTIES = Map.of();

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
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
