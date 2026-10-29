package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.ModifierOrder}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * {@code ModifierOrderCheck}.
 *
 * <p>Checkstyle's check covers two things: keyword modifiers in JLS order, and annotations
 * preceding keyword modifiers. The lint rule only handles the first and skips any declaration
 * carrying an annotation, but the printer re-emits modifiers in canonical order with annotations
 * hoisted ahead of them, so the end-to-end output satisfies both halves.
 */
class ModifierOrderOracleTest {

    ModifierOrderOracleTest() {}

    private static final String CORPUS = "modifier-order";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.modifier.ModifierOrderCheck";

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
