package io.javafmt.lint.oracle;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Differential oracle test for {@link io.javafmt.lint.EmptyBlock}: the rule is warning-only, so
 * every Checkstyle {@code EmptyBlockCheck} violation left in javafmt's output must be matched by a
 * javafmt diagnostic.
 *
 * <p>Two properties align the oracle with javafmt's policy:
 * <ul>
 *   <li>{@code option=text} treats a block holding only a comment as documented-on-purpose, which
 *       is what javafmt does. Checkstyle's default ({@code statement}) would flag it.</li>
 *   <li>{@code tokens} is narrowed to the block kinds javafmt inspects. Checkstyle's default set
 *       leaves out {@code LITERAL_CATCH} and adds {@code LITERAL_IF}, {@code LITERAL_ELSE},
 *       {@code LITERAL_FOR}, {@code LITERAL_WHILE}, {@code LITERAL_DO}, {@code LITERAL_TRY} and
 *       {@code INSTANCE_INIT}. javafmt reports none of those.</li>
 * </ul>
 * javafmt used to warn on an empty method body too, which Checkstyle has no token for. Dogfooding
 * retired it: six warnings on javafmt's own source, every one a deliberate no-op.
 */
class EmptyBlockOracleTest {

    EmptyBlockOracleTest() {}

    private static final String CORPUS = "empty-block";

    private static final String CHECK = "com.puppycrawl.tools.checkstyle.checks.blocks.EmptyBlockCheck";

    private static final Map<String, String> PROPERTIES = Map.of(
            "option",
            "text",
            "tokens",
            "LITERAL_CATCH, LITERAL_FINALLY, LITERAL_SYNCHRONIZED, STATIC_INIT, LITERAL_SWITCH");

    private static final String DIAGNOSTIC_SUBSTRING = "empty ";

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
