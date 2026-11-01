package io.javafmt.intellij;

import io.javafmt.Fixtures;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InProcessFormatterTest {
    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void formatsAWholeFile() {
        assertThat(InProcessFormatter.format(Fixtures.UNFORMATTED_FOO))
                .isInstanceOfSatisfying(
                        JavafmtBinary.Outcome.Formatted.class,
                        formatted -> {
                            assertThat(formatted.output()).isEqualTo(Fixtures.FORMATTED_FOO);
                            assertThat(formatted.diagnostics()).isEmpty();
                        });
    }

    @Test
    void unparseableSourceComesBackUnchangedWithAParseError() {
        final var broken = "class Broken { int x = ; }";
        assertThat(InProcessFormatter.format(broken))
                .isInstanceOfSatisfying(
                        JavafmtBinary.Outcome.Formatted.class,
                        formatted -> {
                            assertThat(formatted.output()).isEqualTo(broken);
                            assertThat(formatted.hasFailure()).isTrue();
                            assertThat(formatted.diagnostics())
                                    .allSatisfy(diagnostic -> {
                                        assertThat(diagnostic.getKind()).isEqualTo(Kind.KIND_PARSE);
                                        assertThat(diagnostic.getSeverity()).isEqualTo(Severity.SEVERITY_ERROR);
                                        assertThat(diagnostic.getLine()).isPositive();
                                    });
                        });
    }

    @Test
    void warningOnlyRulesDoNotCountAsAFailure() {
        assertThat(InProcessFormatter.format("import java.util.*;\n\nclass A {\n    List<String> names;\n}\n"))
                .isInstanceOfSatisfying(
                        JavafmtBinary.Outcome.Formatted.class,
                        formatted -> {
                            assertThat(formatted.hasFailure()).isFalse();
                            assertThat(formatted.diagnostics())
                                    .singleElement()
                                    .satisfies(diagnostic -> {
                                        assertThat(diagnostic.getKind()).isEqualTo(Kind.KIND_WARNING);
                                        assertThat(diagnostic.getSeverity()).isEqualTo(Severity.SEVERITY_WARNING);
                                        assertThat(diagnostic.getMessage()).isNotBlank();
                                    });
                        });
    }

    @Test
    void anInterruptedRequestIsCancelled() {
        Thread.currentThread().interrupt();
        assertThat(InProcessFormatter.format(Fixtures.UNFORMATTED_FOO)).isInstanceOf(JavafmtBinary.Outcome.Cancelled.class);
    }
}
