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
        final var result = new RemoveUnusedImports().apply(LintContext.parseOnly(unit));
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
        final var result = new RemoveUnusedImports().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void emitsEditWhenStaticImportIsUnused() {
        final var unit = JavaParser.parseUnit("""
            import static java.util.Collections.emptyList;

            class Fixture {}
            """);
        final var result = new RemoveUnusedImports().apply(LintContext.parseOnly(unit));
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
        final var result = new RemoveUnusedImports().apply(LintContext.parseOnly(unit));
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
        final var result = new RemoveUnusedImports().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(2);
    }

    @Test
    void leavesStarImportsAloneEvenWhenNoMembersAreReferenced() {
        final var unit = JavaParser.parseUnit("""
            import java.util.*;

            class Fixture {}
            """);
        final var result = new RemoveUnusedImports().apply(LintContext.parseOnly(unit));
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

    @Test
    void moduleImportNotRemovedEvenWhenModuleNameNotReferenced() {
        // Java 25: module imports bring in many types; unused-detection cannot apply to them.
        final var unit = JavaParser.parseUnit("""
            import module java.base;

            class Fixture {}
            """);
        final var result = new RemoveUnusedImports().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void onlyTheUnusedImportOnASharedLineIsDeleted() {
        final var source = """
            import java.util.List; import java.util.Set;

            class Fixture {

                List<String> items = List.of();
            }
            """;
        final var unit = JavaParser.parseUnit(source);
        final var result = new RemoveUnusedImports().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        final var edit = result.edits().getFirst();
        final var rewritten = new StringBuilder(source).replace(edit.start(), edit.end(), edit.replacement()).toString();
        assertThat(rewritten)
                .contains("import java.util.List;")
                .doesNotContain("java.util.Set");
    }

    @Test
    void importReferencedOnlyFromJavadocIsKept() {
        // Javadoc is not part of the parsed AST, so the reference is invisible to the identifier
        // scan. Deleting the import would break any build running -Xdoclint.
        final var unit = JavaParser.parseUnit("""
            import java.util.List;

            /** Companion to {@link List}. */
            class Fixture {

                int count = 0;
            }
            """);
        final var result = new RemoveUnusedImports().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void importNamedOnlyInsideAnUnrelatedWordIsStillRemoved() {
        // "Lists" must not be read as a reference to "List".
        final var unit = JavaParser.parseUnit("""
            import java.util.List;

            /** Nothing to do with Lists here. */
            class Fixture {

                int count = 0;
            }
            """);
        final var result = new RemoveUnusedImports().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
    }
}
