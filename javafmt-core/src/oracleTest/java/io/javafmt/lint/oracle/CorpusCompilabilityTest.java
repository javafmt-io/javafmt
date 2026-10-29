package io.javafmt.lint.oracle;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Non-vacuity guard for the compile assertion that
 * {@link OracleCorpus#assertJavafmtMakesItClean} runs on every corpus file. That assertion
 * skips sources it can't judge in isolation, so a corpus made entirely of snippets referencing
 * off-classpath types would compile nothing and still pass. Each corpus must contain at least
 * one self-contained file.
 *
 * <p>The corpus list comes from the resource tree rather than a literal in this file, so adding a
 * corpus directory enrolls it here automatically.
 */
class CorpusCompilabilityTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource(value = "corpora")
    void corpusHasAtLeastOneCompilableFile(final String corpus) {
        OracleCorpus.assertCorpusIsCompilable(corpus);
    }

    static Stream<String> corpora() {
        return OracleCorpus.corpusNames();
    }
}
