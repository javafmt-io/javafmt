package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UpperEllTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "class Fixture { long x = 1l; }",
        "class Fixture { long x = 0l; }",
        "class Fixture { long x = 100_000l; }",
        "class Fixture { long x = 0xDEADl; }",
    })
    void lowercaseLSuffixGetsReplacedWithUppercase(final String src) {
        final var unit = JavaParser.parseUnit(src);
        final var result = new UpperEll().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("L");
        assertThat(result.diagnostics()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "class Fixture { long x = 1L; }",
        "class Fixture { long x = 0L; }",
        "class Fixture { long x = 100_000L; }",
        "class Fixture { int x = 1; }",
    })
    void upperLSuffixOrNonLongLiteralProducesNoEdits(final String src) {
        final var unit = JavaParser.parseUnit(src);
        final var result = new UpperEll().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void multipleLowercaseLSuffixesAllFixed() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                long a = 1l;
                long b = 2l;
            }
            """);
        final var result = new UpperEll().apply(unit);
        assertThat(result.edits()).hasSize(2);
        assertThat(result.edits()).allMatch(e -> e.replacement().equals("L"));
    }
}
