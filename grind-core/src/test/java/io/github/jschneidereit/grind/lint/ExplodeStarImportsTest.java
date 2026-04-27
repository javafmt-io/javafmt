package io.github.jschneidereit.grind.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.Grind;
import io.github.jschneidereit.grind.parser.JavaParser;

import org.junit.jupiter.api.Test;

class ExplodeStarImportsTest {

    @Test
    void emitsEditWhenStarImportIsPresent() {
        final var unit = JavaParser.parseUnit("""
            import java.util.*;

            class Fixture {
                List<String> items = List.of();
            }
            """);
        final var result = new ExplodeStarImports().apply(unit);
        assertThat(result.edits()).hasSize(1);
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
        final var result = new ExplodeStarImports().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void emitsEditForStaticStarImport() {
        final var unit = JavaParser.parseUnit("""
            import static java.util.Collections.*;

            class Fixture {
                Object items = emptyList();
            }
            """);
        final var result = new ExplodeStarImports().apply(unit);
        assertThat(result.edits()).hasSize(1);
    }

    @Test
    void formatExpandsStarImportToReferencedTypes() {
        final var src = """
            import java.util.*;

            class Fixture {
                List<String> items = List.of();
                Map<String, Integer> map = Map.of();
            }
            """;
        final var formatted = Grind.format(src);
        assertThat(formatted).doesNotContain("java.util.*");
        assertThat(formatted).contains("import java.util.List;");
        assertThat(formatted).contains("import java.util.Map;");
    }

    @Test
    void formatExpandsStaticStarImportToReferencedMembers() {
        final var src = """
            import static java.util.Collections.*;

            class Fixture {
                Object a = emptyList();
                Object b = emptySet();
            }
            """;
        final var formatted = Grind.format(src);
        assertThat(formatted).doesNotContain("Collections.*");
        assertThat(formatted).contains("import static java.util.Collections.emptyList;");
        assertThat(formatted).contains("import static java.util.Collections.emptySet;");
    }

    @Test
    void formatTouchesOnlyStarImportsInMixedFile() {
        final var src = """
            import java.util.HashMap;
            import java.io.*;

            class Fixture {
                HashMap<String, String> m = new HashMap<>();
                File f = new File(".");
            }
            """;
        final var formatted = Grind.format(src);
        assertThat(formatted).doesNotContain("java.io.*");
        assertThat(formatted).contains("import java.io.File;");
        assertThat(formatted).contains("import java.util.HashMap;");
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
        final var first = Grind.format(src);
        final var second = Grind.format(first);
        assertThat(second).isEqualTo(first);
    }
}
