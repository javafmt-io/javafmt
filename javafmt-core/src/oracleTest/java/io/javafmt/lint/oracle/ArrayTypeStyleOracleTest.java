package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.ArrayTypeStyle}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * {@code ArrayTypeStyleCheck}.
 *
 * <p>The lint rule only rewrites variable declarations, and skips multi-variable groups so
 * {@link io.javafmt.lint.MultipleVariableDeclarations} can split them first. C-style brackets on
 * a method return type ({@code int[] first()[]}) are normalized by the printer instead, which
 * emits the return type from the syntax tree rather than from source text.
 */
class ArrayTypeStyleOracleTest {

    ArrayTypeStyleOracleTest() {}

    private static final String CORPUS = "array-type-style";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.ArrayTypeStyleCheck";

    private static final Map<String, String> PROPERTIES = Map.of();

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
