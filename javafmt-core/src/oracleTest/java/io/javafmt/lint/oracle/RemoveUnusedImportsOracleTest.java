package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.RemoveUnusedImports}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * {@code UnusedImportsCheck}.
 *
 * <p>{@code processJavadoc=false} disables Checkstyle's default behavior of treating an
 * import as "used" when it appears only in a Javadoc {@code @link}. Javafmt's rule analyzes
 * source identifiers, not Javadoc bodies, so the oracle is configured to match: an import
 * referenced only from Javadoc counts as unused on both sides.
 */
class RemoveUnusedImportsOracleTest {

    private static final String CORPUS = "remove-unused-imports";

    private static final String CHECK =
        "com.puppycrawl.tools.checkstyle.checks.imports.UnusedImportsCheck";

    private static final Map<String, String> PROPERTIES = Map.of("processJavadoc", "false");

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
