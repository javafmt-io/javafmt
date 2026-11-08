package io.javafmt;

import io.javafmt.cache.FormatCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeSkipTest {

    private static final JavafmtConfig ANALYZE = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(""));

    private static final String STAR_IMPORT = """
            import java.util.*;

            class Order {

                List<String> lines = List.of();
            }
            """;

    private static final String FACTORY_LOCAL = """
            import java.util.ArrayList;
            import java.util.List;
            import java.util.stream.Stream;

            class Invoice {

                void total() {
                    List<String> lines = Stream.of("a").toList();
                    ArrayList<String> copy = new ArrayList<String>();
                    System.out.println(lines + "" + copy);
                }
            }
            """;

    private static final String TYPE_WITNESS = """
            import java.util.stream.Stream;

            class Customer {

                void names() {
                    Stream.<String>of("a", "b").forEach(System.out::println);
                }
            }
            """;

    private static ProjectFile skipping(final String... rules) {
        return new ProjectFile.Settings("/repository/.javafmt", Set.of(rules));
    }

    @ParameterizedTest
    @ValueSource(strings = {STAR_IMPORT, FACTORY_LOCAL, TYPE_WITNESS})
    void skippingEveryRuleThatUsesTheClasspathFormatsAsWithoutIt(final String source) {
        final var skipped = Javafmt.formatWithResult(source, ANALYZE, skipping("ExplodeStarImports", "LocalVarUseVar", "RedundantTypeArguments"));
        assertThat(skipped).isEqualTo(Javafmt.formatWithResult(source));
        assertThat(skipped.output())
                .isNotEqualTo(Javafmt.formatWithResult(source, ANALYZE).output());
    }

    @Test
    void skippingExplodeStarImportsKeepsTheWildcard() {
        assertThat(
                        Javafmt.formatWithResult(STAR_IMPORT, ANALYZE, skipping("ExplodeStarImports"))
                                .output())
                .contains("import java.util.*;");
        assertThat(
                        Javafmt.formatWithResult(STAR_IMPORT, ANALYZE, ProjectFile.none())
                                .output())
                .contains("import java.util.List;");
    }

    @Test
    void skippingLocalVarUseVarKeepsOnlyItsRewritesThatNeedNoClasspath() {
        final var output = Javafmt.formatWithResult(FACTORY_LOCAL, ANALYZE, skipping("LocalVarUseVar"))
                .output();
        assertThat(output).contains("final List<String> lines = Stream.of(\"a\").toList();", "final var copy = new ArrayList<String>();");
    }

    @Test
    void skippingRedundantTypeArgumentsKeepsTheTypeWitness() {
        assertThat(
                        Javafmt.formatWithResult(TYPE_WITNESS, ANALYZE, skipping("RedundantTypeArguments"))
                                .output())
                .contains("Stream.<String>of");
        assertThat(
                        Javafmt.formatWithResult(TYPE_WITNESS, ANALYZE, ProjectFile.none())
                                .output())
                .doesNotContain("Stream.<String>of");
    }

    @Test
    void skippingOneRuleLeavesTheOthersTheirClasspath() {
        final var output = Javafmt.formatWithResult(FACTORY_LOCAL, ANALYZE, skipping("RedundantTypeArguments", "ExplodeStarImports"))
                .output();
        assertThat(output).contains("final var lines = Stream.of(\"a\").toList();");
    }

    @Test
    void aSkippedRuleStillSeesWhatTheRulesItDefersToDoWithTheClasspath() {
        final var source = """
                import java.util.ArrayList;
                import java.util.List;

                class Shipment {

                    void pack() {
                        List<String> boxes = new ArrayList<String>();
                        boxes.add("a");
                    }
                }
                """;
        assertThat(
                        Javafmt.formatWithResult(source, ANALYZE, skipping("RedundantTypeArguments"))
                                .output())
                .contains("final var boxes = new ArrayList<String>();");
    }

    @Test
    void anInvalidProjectFileLeavesTheSourceUnchangedWithAConfigurationError() {
        final var invalid = ProjectFile.parse("/repository/.javafmt", "analyze.skip=FinalParameters\n");
        final var result = Javafmt.formatWithResult(STAR_IMPORT, ANALYZE, invalid);
        assertThat(result.output()).isEqualTo(STAR_IMPORT);
        assertThat(result.diagnostics())
                .singleElement()
                .isInstanceOfSatisfying(Diagnostic.ConfigError.class,
                        error -> assertThat(error.message()).contains("/repository/.javafmt", "FinalParameters"));
    }

    @Test
    void formatFilesAppliesEachFilesOwnProjectFile(@TempDir final Path dir) throws IOException {
        final var skipping = Files.createDirectories(dir.resolve("skipping"));
        Files.createDirectories(skipping.resolve(".git"));
        Files.writeString(skipping.resolve(".javafmt"), "analyze.skip=ExplodeStarImports\n");
        final var plain = Files.createDirectories(dir.resolve("plain"));
        Files.createDirectories(plain.resolve(".git"));
        final var skipped = Files.writeString(skipping.resolve("Order.java"), STAR_IMPORT);
        final var exploded = Files.writeString(plain.resolve("Invoice.java"), STAR_IMPORT.replace("Order", "Invoice"));
        final var results = Javafmt.formatFiles(List.of(skipped, exploded), ANALYZE, FormatCache.noop(), 2);
        assertThat(((FileResult.Formatted) results.getFirst()).result().output())
                .contains("import java.util.*;");
        assertThat(((FileResult.Formatted) results.get(1)).result().output()).contains("import java.util.List;");
    }

    @Test
    void formatFilesReportsAnInvalidProjectFileOnEveryFileItGoverns(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=Unknown\n");
        final var source = Files.writeString(dir.resolve("Order.java"), STAR_IMPORT);
        final var results = Javafmt.formatFiles(List.of(source), JavafmtConfig.defaults(), FormatCache.noop(), 1);
        assertThat(results)
                .singleElement()
                .isInstanceOfSatisfying(FileResult.Formatted.class, formatted -> {
                    assertThat(formatted.changed()).isFalse();
                    assertThat(formatted.result().errors())
                            .singleElement()
                            .isInstanceOf(Diagnostic.ConfigError.class);
                });
    }
}
