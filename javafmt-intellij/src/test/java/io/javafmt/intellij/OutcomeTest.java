package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.daemon.protocol.Diagnostic;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OutcomeTest {

    @ParameterizedTest
    @CsvSource({ "SEVERITY_WARNING,KIND_WARNING,false,false", "SEVERITY_ERROR,KIND_PARSE,true,false", "SEVERITY_ERROR,KIND_LINT,true,false", "SEVERITY_ERROR,KIND_CLI,true,false", "SEVERITY_ERROR,KIND_INTERNAL,true,true", "SEVERITY_UNSPECIFIED,KIND_UNSPECIFIED,true,false", })
    void onlyAWarningIsAdvisoryAndOnlyAnInternalOneIsJavafmtsFault(
            final Severity severity,
            final Kind kind,
            final boolean failure,
            final boolean internal) {
        final var formatted = new JavafmtBinary.Outcome.Formatted("x", List.of(FakeDaemon.diagnostic(severity, kind, "m", 1, 1)));
        assertThat(formatted.hasFailure()).isEqualTo(failure);
        assertThat(formatted.hasInternalError()).isEqualTo(internal);
    }

    @Test
    void aSeverityThisBuildHasNeverHeardOfIsAFailure() {
        final var formatted = new JavafmtBinary.Outcome.Formatted("x", List.of(Diagnostic.newBuilder().setSeverityValue(7).build()));
        assertThat(formatted.hasFailure()).isTrue();
    }
}
