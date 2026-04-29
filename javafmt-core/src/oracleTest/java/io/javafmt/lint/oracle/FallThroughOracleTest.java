package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.FallThrough}:
 * for every corpus file, either javafmt's output is Checkstyle-clean or javafmt reports a
 * matching diagnostic. Since FallThrough produces no edits, "auto-fix path" never
 * applies — the assertion always falls through (heh) to the diagnostic-match branch for
 * dirty corpus files.
 */
class FallThroughOracleTest {

    private static final String CORPUS = "fall-through";

    private static final String CHECK =
        "com.puppycrawl.tools.checkstyle.checks.coding.FallThroughCheck";

    private static final Map<String, String> PROPERTIES = Map.of();

    private static final String DIAGNOSTIC_SUBSTRING = "falls through";

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void javafmtFixesOrReports(final String name, final String source) {
        OracleCorpus.assertJavafmtFixesOrReports(name, source, DIAGNOSTIC_SUBSTRING, CHECK, PROPERTIES);
    }

    @Test
    void corpusContainsViolationsToBegin() {
        OracleCorpus.assertCorpusExercisesRule(CORPUS, CHECK, PROPERTIES);
    }

    static Stream<Arguments> corpus() {
        return OracleCorpus.load(CORPUS);
    }
}
