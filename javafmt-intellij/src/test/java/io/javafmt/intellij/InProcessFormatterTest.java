package io.javafmt.intellij;

import io.javafmt.Fixtures;
import io.javafmt.JavafmtConfig;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InProcessFormatterTest {

    private static final String STAR_IMPORT = "import java.util.*;\n\nclass Order {\n\n    List<String> lines;\n}\n";

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void formatsAWholeFile() {
        assertThat(InProcessFormatter.format(Fixtures.UNFORMATTED_FOO, FileScope.none()))
                .isInstanceOfSatisfying(JavafmtBinary.Outcome.Formatted.class, formatted -> {
                    assertThat(formatted.output()).isEqualTo(Fixtures.FORMATTED_FOO);
                    assertThat(formatted.diagnostics()).isEmpty();
                });
    }

    @Test
    void unparseableSourceComesBackUnchangedWithAParseError() {
        final var broken = "class Broken { int x = ; }";
        assertThat(InProcessFormatter.format(broken, FileScope.none())).isInstanceOfSatisfying(JavafmtBinary.Outcome.Formatted.class, formatted -> {
            assertThat(formatted.output()).isEqualTo(broken);
            assertThat(formatted.hasFailure()).isTrue();
            assertThat(formatted.diagnostics()).allSatisfy(diagnostic -> {
                assertThat(diagnostic.getKind()).isEqualTo(Kind.KIND_PARSE);
                assertThat(diagnostic.getSeverity()).isEqualTo(Severity.SEVERITY_ERROR);
                assertThat(diagnostic.getLine()).isPositive();
            });
        });
    }

    @Test
    void warningOnlyRulesDoNotCountAsAFailure() {
        assertThat(InProcessFormatter.format("import java.util.*;\n\nclass A {\n\n    List<String> names;\n}\n", FileScope.none()))
                .isInstanceOfSatisfying(JavafmtBinary.Outcome.Formatted.class, formatted -> {
                    assertThat(formatted.hasFailure()).isFalse();
                    assertThat(formatted.diagnostics()).singleElement().satisfies(diagnostic -> {
                        assertThat(diagnostic.getKind()).isEqualTo(Kind.KIND_WARNING);
                        assertThat(diagnostic.getSeverity()).isEqualTo(Severity.SEVERITY_WARNING);
                        assertThat(diagnostic.getMessage()).isNotBlank();
                    });
                });
    }

    @Test
    void aClasspathTurnsOnTheRulesThatNeedIt() {
        assertThat(InProcessFormatter.format(STAR_IMPORT, new FileScope(Optional.empty(), new JavafmtConfig.Analysis.Attributed(""))))
                .isInstanceOfSatisfying(
                        JavafmtBinary.Outcome.Formatted.class,
                        formatted -> assertThat(formatted.output()).contains("import java.util.List;").doesNotContain("java.util.*"));
    }

    @Test
    void withoutAClasspathTheRulesThatNeedOneDoNotRun() {
        assertThat(InProcessFormatter.format(STAR_IMPORT, FileScope.none()))
                .isInstanceOfSatisfying(
                        JavafmtBinary.Outcome.Formatted.class, formatted -> assertThat(formatted.output()).contains("import java.util.*;"));
    }

    @Test
    void theProjectFileForTheFilesPathApplies(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=ExplodeStarImports\n");
        final var scope = new FileScope(Optional.of(dir.resolve("src/Order.java")), new JavafmtConfig.Analysis.Attributed(""));
        assertThat(InProcessFormatter.format(STAR_IMPORT, scope))
                .isInstanceOfSatisfying(
                        JavafmtBinary.Outcome.Formatted.class, formatted -> assertThat(formatted.output()).contains("import java.util.*;"));
    }

    @Test
    void anInvalidProjectFileIsAConfigurationError(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=FinalParameters\n");
        assertThat(
                InProcessFormatter.format(STAR_IMPORT, new FileScope(Optional.of(dir.resolve("Order.java")), new JavafmtConfig.Analysis.ParseOnly())))
                .isInstanceOfSatisfying(JavafmtBinary.Outcome.Formatted.class, formatted -> {
                    assertThat(formatted.output()).isEqualTo(STAR_IMPORT);
                    assertThat(formatted.hasFailure()).isTrue();
                    assertThat(formatted.diagnostics()).singleElement().satisfies(diagnostic -> {
                        assertThat(diagnostic.getKind()).isEqualTo(Kind.KIND_CONFIG);
                        assertThat(diagnostic.getMessage()).contains(".javafmt", "FinalParameters");
                    });
                });
    }

    @Test
    void anInterruptedRequestIsCancelled() {
        Thread.currentThread().interrupt();
        assertThat(InProcessFormatter.format(Fixtures.UNFORMATTED_FOO, FileScope.none())).isInstanceOf(JavafmtBinary.Outcome.Cancelled.class);
    }
}
