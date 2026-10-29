package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.HideUtilityClassConstructor}: the rule is
 * warning-only, so every Checkstyle {@code HideUtilityClassConstructorCheck} violation left in
 * javafmt's output must be matched by a javafmt diagnostic.
 */
class HideUtilityClassConstructorOracleTest {

    HideUtilityClassConstructorOracleTest() {}

    private static final String CORPUS = "hide-utility-class-constructor";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.design.HideUtilityClassConstructorCheck";

    private static final Map<String, String> PROPERTIES = Map.of();

    private static final String DIAGNOSTIC_SUBSTRING = "private constructor";

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
