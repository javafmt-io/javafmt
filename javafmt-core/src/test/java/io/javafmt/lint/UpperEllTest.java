package io.javafmt.lint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class UpperEllTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "class Fixture { long x = 1l; }|class Fixture { long x = 1L; }",
            "class Fixture { long x = 0l; }|class Fixture { long x = 0L; }",
            "class Fixture { long x = 100_000l; }|class Fixture { long x = 100_000L; }",
            "class Fixture { long x = 0xDEADl; }|class Fixture { long x = 0xDEADL; }",
    })
    void lowercaseLSuffixGetsReplacedWithUppercase(final String src, final String expected) {
        final var result = LintFixtures.apply(new UpperEll(), src);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(LintFixtures.fix(new UpperEll(), src)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class Fixture { long x = 1L; }",
            "class Fixture { long x = 0L; }",
            "class Fixture { long x = 100_000L; }",
            "class Fixture { int x = 1; }",
    })
    void upperLSuffixOrNonLongLiteralProducesNoEdits(final String src) {
        assertThat(LintFixtures.apply(new UpperEll(), src).edits()).isEmpty();
        assertThat(LintFixtures.fix(new UpperEll(), src)).isEqualTo(src);
    }

    @Test
    void multipleLowercaseLSuffixesAllFixed() {
        final var src = "class Fixture {\n\n    long a = 1l;\n    long b = 2l;\n}\n";
        assertThat(LintFixtures.apply(new UpperEll(), src).edits()).hasSize(2);
        assertThat(LintFixtures.fix(new UpperEll(), src)).isEqualTo("class Fixture {\n\n    long a = 1L;\n    long b = 2L;\n}\n");
    }
}
