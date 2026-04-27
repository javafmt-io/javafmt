package io.github.jschneidereit.grind.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.github.jschneidereit.grind.lint.FallThrough}:
 * for every corpus file, either grind's output is Checkstyle-clean or grind reports a
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
