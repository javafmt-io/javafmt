package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.MissingSwitchDefault}: the rule is
 * warning-only, so every Checkstyle {@code MissingSwitchDefaultCheck} violation left in javafmt's
 * output must be matched by a javafmt diagnostic. The corpus covers colon and arrow form, both of
 * which are switch statements rather than switch expressions — the compiler already demands
 * exhaustiveness from an expression.
 */
class MissingSwitchDefaultOracleTest {

    MissingSwitchDefaultOracleTest() {}

    private static final String CORPUS = "missing-switch-default";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.coding.MissingSwitchDefaultCheck";

    private static final Map<String, String> PROPERTIES = Map.of();

    private static final String DIAGNOSTIC_SUBSTRING = "'default' case";

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
