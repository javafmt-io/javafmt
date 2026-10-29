package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Javafmt;
import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

/**
 * Parse-only behavior. Deciding which members a wildcard supplies needs resolved symbols, so
 * without attribution the rule does nothing at all; see {@link ExplodeStarImportsAnalyzeTest} for
 * the cases where it fires.
 */
class ExplodeStarImportsTest {

    @Test
    void emitsNoEditForStarImportWithoutAttribution() {
        final var unit = JavaParser.parseUnit("""
            import java.util.*;

            class Fixture {

                List<String> items = List.of();
            }
            """);
        final var result = new ExplodeStarImports().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void emitsNoEditForFileWithoutStarImports() {
        final var unit = JavaParser.parseUnit("""
            import java.util.List;

            class Fixture {

                List<String> items = List.of();
            }
            """);
        final var result = new ExplodeStarImports().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void emitsNoEditForStaticStarImportWithoutAttribution() {
        final var unit = JavaParser.parseUnit("""
            import static java.util.Collections.*;

            class Fixture {

                Object items = emptyList();
            }
            """);
        final var result = new ExplodeStarImports().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void formatLeavesStarImportsAlone() {
        final var src = """
            import java.util.*;

            class Fixture {

                List<String> items = List.of();
                Map<String, Integer> map = Map.of();
            }
            """;
        assertThat(Javafmt.format(src)).contains("import java.util.*;");
    }

    @Test
    void isIdempotent() {
        final var src = """
            import java.util.*;

            class Fixture {

                List<String> items = List.of();
                Map<String, Integer> map = Map.of();
            }
            """;
        final var first = Javafmt.format(src);
        assertThat(Javafmt.format(first)).isEqualTo(first);
    }
}
