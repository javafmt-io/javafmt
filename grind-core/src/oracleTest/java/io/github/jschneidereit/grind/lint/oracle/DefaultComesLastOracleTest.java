package io.github.jschneidereit.grind.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.github.jschneidereit.grind.lint.DefaultComesLast}:
 * for every corpus file, either grind auto-fixes the misplaced default (output is
 * Checkstyle-clean) or grind emits a matching diagnostic. Both behaviors are valid
 * outcomes — the helper accepts either.
 */
class DefaultComesLastOracleTest {

    private static final String CORPUS = "default-comes-last";

    private static final String CHECK =
        "com.puppycrawl.tools.checkstyle.checks.coding.DefaultComesLastCheck";

    private static final Map<String, String> PROPERTIES = Map.of();

    private static final String DIAGNOSTIC_SUBSTRING = "default case should be last";

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void grindFixesOrReports(final String name, final String source) {
        OracleCorpus.assertGrindFixesOrReports(name, source, DIAGNOSTIC_SUBSTRING, CHECK, PROPERTIES);
    }

    @Test
    void corpusContainsViolationsToBegin() {
        OracleCorpus.assertCorpusExercisesRule(CORPUS, CHECK, PROPERTIES);
    }

    static Stream<Arguments> corpus() {
        return OracleCorpus.load(CORPUS);
    }
}
