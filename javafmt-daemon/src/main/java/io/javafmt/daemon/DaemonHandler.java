package io.javafmt.daemon;

import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.daemon.protocol.Diagnostic;
import io.javafmt.daemon.protocol.FormatReply;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import lombok.experimental.UtilityClass;

@UtilityClass
final class DaemonHandler {

    FormatReply handle(final int id, final String source) {
        final var result = Javafmt.formatWithResult(source, JavafmtConfig.defaults());
        return FormatReply.newBuilder()
                .setId(id)
                .setOutput(result.output())
                .addAllDiagnostics(result.diagnostics().stream().map(DaemonHandler::diagnostic).toList())
                .build();
    }

    FormatReply failure(final int id, final String output, final Kind kind, final String message) {
        return FormatReply.newBuilder()
                .setId(id)
                .setOutput(output)
                .addDiagnostics(Diagnostic.newBuilder().setSeverity(Severity.SEVERITY_ERROR).setKind(kind).setMessage(message))
                .build();
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
            case io.javafmt.Diagnostic.ParseError ignored -> Kind.KIND_PARSE;
            case io.javafmt.Diagnostic.LintError ignored -> Kind.KIND_LINT;
            case io.javafmt.Diagnostic.InternalError ignored -> Kind.KIND_INTERNAL;
            case io.javafmt.Diagnostic.Warning ignored -> Kind.KIND_WARNING;
        };
    }
}
