package io.github.jschneidereit.grind.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.github.jschneidereit.grind.lint.ExplodeStarImports}:
 * grind's output for every corpus file must contain zero violations of Checkstyle's
 * {@code AvoidStarImportCheck}.
 *
 * <p>{@code allowClassImports} and {@code allowStaticImports} both default to {@code false},
 * matching grind's strict policy: any wildcard import (static or not) must be expanded to
 * the explicit members actually referenced in the file.
 */
class ExplodeStarImportsOracleTest {

    private static final String CORPUS = "explode-star-imports";

    private static final String CHECK =
        "com.puppycrawl.tools.checkstyle.checks.imports.AvoidStarImportCheck";

    private static final Map<String, String> PROPERTIES = Map.of();

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void grindOutputIsCheckstyleClean(final String name, final String source) {
        OracleCorpus.assertGrindMakesItClean(name, source, CHECK, PROPERTIES);
    }

    @Test
    void corpusContainsViolationsToBegin() {
        OracleCorpus.assertCorpusExercisesRule(CORPUS, CHECK, PROPERTIES);
    }

    static Stream<Arguments> corpus() {
        return OracleCorpus.load(CORPUS);
    }
}
