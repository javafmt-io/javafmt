package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Javafmt;
import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class RemoveUnusedImportsTest {

    @Test
    void emitsEditWhenSingleImportIsUnused() {
        final var unit = JavaParser.parseUnit("""
            import java.util.List;

            class Fixture {}
            """);
        final var result = new RemoveUnusedImports().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void emitsNoEditWhenImportIsReferencedAsType() {
        final var unit = JavaParser.parseUnit("""
            import java.util.List;

            class Fixture {
                List<String> items = List.of();
            }
            """);
        final var result = new RemoveUnusedImports().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void emitsEditWhenStaticImportIsUnused() {
        final var unit = JavaParser.parseUnit("""
            import static java.util.Collections.emptyList;

            class Fixture {}
            """);
        final var result = new RemoveUnusedImports().apply(unit);
        assertThat(result.edits()).hasSize(1);
    }

    @Test
    void emitsNoEditWhenStaticImportIsReferenced() {
        final var unit = JavaParser.parseUnit("""
            import static java.util.Collections.emptyList;

            class Fixture {
                Object items = emptyList();
            }
            """);
        final var result = new RemoveUnusedImports().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void emitsOneEditPerUnusedImportInMixedFile() {
        final var unit = JavaParser.parseUnit("""
            import java.util.List;
            import java.util.Map;
            import java.util.Set;

            class Fixture {
                List<String> items = List.of();
            }
            """);
        final var result = new RemoveUnusedImports().apply(unit);
        assertThat(result.edits()).hasSize(2);
    }

    @Test
    void leavesStarImportsAloneEvenWhenNoMembersAreReferenced() {
        final var unit = JavaParser.parseUnit("""
            import java.util.*;

            class Fixture {}
            """);
        final var result = new RemoveUnusedImports().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void formatRemovesUnusedImportsEndToEnd() {
        final var src = """
            import java.util.List;
            import java.util.Map;

            class Fixture {
                Map<String, String> m = Map.of();
            }
            """;
        final var formatted = Javafmt.format(src);
        assertThat(formatted).doesNotContain("java.util.List");
        assertThat(formatted).contains("java.util.Map");
    }

    @Test
    void isIdempotent() {
        final var src = """
            import java.util.List;
            import java.util.Map;

            class Fixture {
                Map<String, String> m = Map.of();
            }
            """;
        final var first = Javafmt.format(src);
        final var second = Javafmt.format(first);
        assertThat(second).isEqualTo(first);
    }
}
