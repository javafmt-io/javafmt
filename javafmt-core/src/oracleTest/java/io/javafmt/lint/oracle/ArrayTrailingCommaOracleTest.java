package io.javafmt.lint.oracle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Differential oracle test for {@link io.javafmt.lint.ArrayTrailingComma}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * equivalent {@code ArrayTrailingCommaCheck}.
 *
 * <p>{@code alwaysDemandTrailingComma=false}, Checkstyle's default, matches javafmt: the
 * trailing comma is demanded only when the closing brace is on its own line.
 */
class ArrayTrailingCommaOracleTest {

    ArrayTrailingCommaOracleTest() {}

    private static final String CORPUS = "array-trailing-comma";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.coding.ArrayTrailingCommaCheck";

    private static final Map<String, String> PROPERTIES = Map.of("alwaysDemandTrailingComma", "false");

    @ParameterizedTest(name = "{0}")
    @MethodSource(value = "corpus")
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
