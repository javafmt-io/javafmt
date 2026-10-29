package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.RedundantModifier}:
 * javafmt's output for every corpus file must contain zero violations of Checkstyle's
 * {@code RedundantModifierCheck}.
 *
 * <p>Checkstyle's check is broader than javafmt's rule. The corpus covers the shared ground —
 * {@code public}/{@code abstract} on interface methods, {@code public static final} on interface
 * fields, {@code static} on a nested interface or enum, and {@code final} on a method of a
 * {@code final} class. Cases Checkstyle flags and javafmt leaves alone are listed here rather than
 * placed in the corpus, where they would only assert that the gap still exists:
 * <ul>
 *   <li>{@code static} on a nested record or annotation type (the rule handles interfaces and
 *       enums only, though the docs claim records too)</li>
 *   <li>{@code private} on an enum constructor</li>
 *   <li>{@code strictfp} anywhere (implied since Java 17)</li>
 *   <li>{@code final} on a parameter of an abstract or interface method</li>
 *   <li>{@code final} on a try-with-resources resource</li>
 * </ul>
 */
class RedundantModifierOracleTest {

    RedundantModifierOracleTest() {}

    private static final String CORPUS = "redundant-modifier";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.modifier.RedundantModifierCheck";

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
