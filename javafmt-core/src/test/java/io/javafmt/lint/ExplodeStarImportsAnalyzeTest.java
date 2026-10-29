package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import org.junit.jupiter.api.Test;

class ExplodeStarImportsAnalyzeTest {

    private static final JavafmtConfig ANALYZE = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(""));

    @Test
    void starImportIsLeftAloneWithoutAttribution() {
        // Membership can only be decided from resolved symbols. Parse-only runs leave the wildcard
        // in place; AvoidStarImport still reports it as a warning.
        final var source = """
                import java.util.*;

                class Fixture {

                    List<String> items = List.of();
                }
                """;
        assertThat(Javafmt.format(source)).contains("import java.util.*;");
    }

    @Test
    void referencedTypesBecomeExplicitImportsUnderAnalyze() {
        final var source = """
                import java.util.*;

                class Fixture {

                    List<String> items = List.of();

                    Map<String, Integer> map = Map.of();
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted)
                .contains("import java.util.List;")
                .contains("import java.util.Map;")
                .doesNotContain("java.util.*");
    }

    @Test
    void staticStarImportBecomesExplicitStaticImports() {
        final var source = """
                import static java.util.Collections.*;

                class Fixture {

                    Object a = emptyList();

                    Object b = emptySet();
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted)
                .contains("import static java.util.Collections.emptyList;")
                .contains("import static java.util.Collections.emptySet;")
                .doesNotContain("Collections.*");
    }

    @Test
    void aNameTheOwnPackageSuppliesIsNotRebound() {
        // `LintEdit` lives in this file's own package and needs no import. Reflection against
        // javafmt's classloader can only ask "does java.util.LintEdit exist"; resolved symbols say
        // where the name actually came from, so nothing spurious is imported.
        final var source = """
                package io.javafmt.lint;

                import java.util.*;

                class Fixture {

                    List<String> items = List.of();

                    LintEdit edit = LintEdit.insert(0, "x");
                }
                """;
        final var formatted = Javafmt.formatWithResult(
                source,
                JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(System.getProperty("java.class.path"))))
                .output();
        assertThat(formatted)
                .contains("import java.util.List;")
                .doesNotContain("LintEdit;");
    }

    @Test
    void unresolvableIdentifierLeavesTheStarImportAlone() {
        // With an off-classpath type in the file we cannot prove which names the wildcard supplies,
        // so exploding it could drop an import the file still needs.
        final var source = """
                import java.util.*;

                class Fixture {

                    List<String> items = List.of();

                    OffClasspath thing = null;
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("import java.util.*;");
    }

    @Test
    void onlyStarImportsAreTouchedInAMixedFile() {
        final var source = """
                import java.util.HashMap;
                import java.io.*;

                class Fixture {

                    HashMap<String, String> m = new HashMap<>();

                    File f = new File(".");
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted)
                .doesNotContain("java.io.*")
                .contains("import java.io.File;")
                .contains("import java.util.HashMap;");
    }

    @Test
    void isIdempotent() {
        final var source = """
                import java.util.*;

                class Fixture {

                    List<String> items = List.of();

                    Map<String, Integer> map = Map.of();
                }
                """;
        final var first = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(Javafmt.formatWithResult(first, ANALYZE).output()).isEqualTo(first);
    }
}
