package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.MultipleVariableDeclarations}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * {@code MultipleVariableDeclarationsCheck}.
 *
 * <p>Checkstyle's check reports two things: several declarators in one statement
 * ({@code int a, b;}) and several declaration statements on one line ({@code int a = 1; int b = 2;}).
 * The lint rule handles the first; the second falls out of
 * {@link io.javafmt.lint.OneStatementPerLine} plus the printer's one-statement-per-line layout.
 */
class MultipleVariableDeclarationsOracleTest {

    MultipleVariableDeclarationsOracleTest() {}

    private static final String CORPUS = "multiple-variable-declarations";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.coding.MultipleVariableDeclarationsCheck";

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
