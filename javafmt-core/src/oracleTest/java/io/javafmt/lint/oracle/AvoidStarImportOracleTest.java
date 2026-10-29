package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.AvoidStarImport}: the rule is warning-only,
 * so every Checkstyle {@code AvoidStarImportCheck} violation left in javafmt's output must be
 * matched by a javafmt diagnostic. When run with a classpath,
 * {@link io.javafmt.lint.ExplodeStarImports} removes the star import outright and the output is
 * clean instead — the helper accepts either outcome.
 *
 * <p>{@code allowStaticMemberImports=true} matches javafmt's split of responsibilities:
 * {@code AvoidStarImport} covers non-static wildcards only, since expanding a static wildcard
 * means enumerating static members and belongs to {@code ExplodeStarImports}.
 */
class AvoidStarImportOracleTest {

    AvoidStarImportOracleTest() {}

    private static final String CORPUS = "avoid-star-import";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.imports.AvoidStarImportCheck";

    private static final Map<String, String> PROPERTIES = Map.of("allowStaticMemberImports", "true");

    private static final String DIAGNOSTIC_SUBSTRING = "avoid star import";

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
