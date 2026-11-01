package io.javafmt.intellij;

import io.javafmt.Javafmt;
import io.javafmt.daemon.protocol.Diagnostic;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import lombok.experimental.UtilityClass;

/**
 * Formats with {@code javafmt-core} inside the IDE, answering in the same {@link JavafmtBinary.Outcome}
 * shape and with the same diagnostics the native daemon would, so either engine is reported alike.
 */
@UtilityClass
class InProcessFormatter {
    JavafmtBinary.Outcome format(final String source) {
        if (Thread.currentThread().isInterrupted()) {
            return new JavafmtBinary.Outcome.Cancelled();
        }
        final var result = Javafmt.formatWithResult(source);
        if (Thread.currentThread().isInterrupted()) {
            return new JavafmtBinary.Outcome.Cancelled();
        }
        return new JavafmtBinary.Outcome.Formatted(result.output(), result.diagnostics().stream().map(InProcessFormatter::diagnostic).toList());
    }

    private Diagnostic diagnostic(final io.javafmt.Diagnostic diagnostic) {
        return Diagnostic.newBuilder()
                .setSeverity(diagnostic.isError() ? Severity.SEVERITY_ERROR : Severity.SEVERITY_WARNING)
                .setKind(kind(diagnostic))
                .setMessage(diagnostic.message())
                .setLine(diagnostic.position().line())
                .setColumn(diagnostic.position().column())
                .build();
    }

    private Kind kind(final io.javafmt.Diagnostic diagnostic) {
        return switch (diagnostic) {
            case io.javafmt.Diagnostic.ParseError _ -> Kind.KIND_PARSE;
            case io.javafmt.Diagnostic.LintError _ -> Kind.KIND_LINT;
            case io.javafmt.Diagnostic.InternalError _ -> Kind.KIND_INTERNAL;
            case io.javafmt.Diagnostic.Warning _ -> Kind.KIND_WARNING;
        };
    }
}
