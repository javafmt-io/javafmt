package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.FinalLocalVariable}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * equivalent {@code FinalLocalVariableCheck}.
 *
 * <p>Checkstyle's defaults match javafmt's scope: {@code VARIABLE_DEF} only (no parameters,
 * no enhanced-for variables). Divergences from this default are configured here, with a
 * comment explaining why.
 */
class FinalLocalVariableOracleTest {

    private static final String CORPUS = "final-local-variable";

    private static final String CHECK =
        "com.puppycrawl.tools.checkstyle.checks.coding.FinalLocalVariableCheck";

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
