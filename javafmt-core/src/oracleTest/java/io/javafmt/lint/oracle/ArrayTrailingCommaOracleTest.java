package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.ArrayTrailingComma}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * equivalent {@code ArrayTrailingCommaCheck}.
 *
 * <p>{@code alwaysDemandTrailingComma=true} matches javafmt's strict policy: every
 * non-empty array initializer gets a trailing comma, including single-line ones.
 * Checkstyle's default ({@code false}) only demands the trailing comma when the closing
 * brace is on its own line; javafmt goes strict because the trailing comma minimizes diff
 * noise on later edits and is uniform across single- and multi-line forms.
 */
class ArrayTrailingCommaOracleTest {

    private static final String CORPUS = "array-trailing-comma";

    private static final String CHECK =
        "com.puppycrawl.tools.checkstyle.checks.coding.ArrayTrailingCommaCheck";

    private static final Map<String, String> PROPERTIES = Map.of("alwaysDemandTrailingComma", "true");

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
