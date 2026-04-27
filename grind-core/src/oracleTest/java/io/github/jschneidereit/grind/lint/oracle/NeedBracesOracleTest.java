package io.github.jschneidereit.grind.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.github.jschneidereit.grind.lint.NeedBraces}:
 * grind's output for every corpus file must contain zero violations of Checkstyle's
 * equivalent {@code NeedBracesCheck}.
 *
 * <p>Checkstyle defaults cover {@code LITERAL_IF}, {@code LITERAL_ELSE},
 * {@code LITERAL_FOR}, {@code LITERAL_WHILE}, {@code LITERAL_DO}, which match grind's
 * v1 scope. {@code LITERAL_CASE} (switch case bodies) is intentionally left off — out
 * of scope for v1.
 */
class NeedBracesOracleTest {

    private static final String CORPUS = "need-braces";

    private static final String CHECK =
        "com.puppycrawl.tools.checkstyle.checks.blocks.NeedBracesCheck";

    private static final Map<String, String> PROPERTIES = Map.of();

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void grindOutputIsCheckstyleClean(final String name, final String source) {
        OracleCorpus.assertGrindMakesItClean(name, source, CHECK, PROPERTIES);
    }

    @Test
    void corpusContainsViolationsToBegin() {
        OracleCorpus.assertCorpusExercisesRule(CORPUS, CHECK, PROPERTIES);
    }

    static Stream<Arguments> corpus() {
        return OracleCorpus.load(CORPUS);
    }
}
