package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.CovariantEquals}: the rule is warning-only,
 * so every Checkstyle {@code CovariantEqualsCheck} violation left in javafmt's output must be
 * matched by a javafmt diagnostic.
 *
 * <p>javafmt is the stricter of the two: Checkstyle stays quiet once a proper
 * {@code equals(Object)} sits alongside the overload, while javafmt flags the overload either
 * way. Warning about more than the oracle demands cannot break soundness.
 */
class CovariantEqualsOracleTest {

    CovariantEqualsOracleTest() {}

    private static final String CORPUS = "covariant-equals";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.coding.CovariantEqualsCheck";

    private static final Map<String, String> PROPERTIES = Map.of();

    private static final String DIAGNOSTIC_SUBSTRING = "covariant equals";

    @ParameterizedTest(name = "{0}")
    @MethodSource(value = "corpus")
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
