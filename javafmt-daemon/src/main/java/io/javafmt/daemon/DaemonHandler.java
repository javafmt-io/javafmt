package io.javafmt.daemon;

import io.javafmt.Diagnostic;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.ProjectFile;
import io.javafmt.daemon.protocol.FormatReply;
import io.javafmt.daemon.protocol.FormatRequest;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import lombok.experimental.UtilityClass;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@UtilityClass
final class DaemonHandler {

    FormatReply handle(final FormatRequest request) {
        if (!request.hasSource()) {
            return failure(request.getId(), "", Kind.KIND_CLI, "source: value is required");
        }
        final ProjectFile projectFile;
        try {
            projectFile = request.hasPath() ? ProjectFile.forFile(Path.of(request.getPath())) : ProjectFile.none();
        } catch (final InvalidPathException e) {
            return failure(request.getId(), request.getSource(), Kind.KIND_CLI, "path: " + e.getMessage());
        }
        final var defaults = JavafmtConfig.defaults();
        final var config = request.hasClasspath() ? defaults.withAnalysis(new JavafmtConfig.Analysis.Attributed(request.getClasspath())) : defaults;
        final var result = Javafmt.formatWithResult(request.getSource(), config, projectFile);
        return FormatReply.newBuilder()
                .setId(request.getId())
                .setOutput(result.output())
                .addAllDiagnostics(result.diagnostics().stream()
                        .map(DaemonHandler::diagnostic)
                        .toList())
                .build();
    }

    FormatReply failure(final int id, final String output, final Kind kind, final String message) {
        return FormatReply.newBuilder()
                .setId(id)
                .setOutput(output)
                .addDiagnostics(io.javafmt.daemon.protocol.Diagnostic.newBuilder()
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .setKind(kind)
                        .setMessage(message))
                .build();
    }

    io.javafmt.daemon.protocol.Diagnostic diagnostic(final Diagnostic diagnostic) {
        return io.javafmt.daemon.protocol.Diagnostic.newBuilder()
                .setSeverity(diagnostic.isError() ? Severity.SEVERITY_ERROR : Severity.SEVERITY_WARNING)
                .setKind(kind(diagnostic))
                .setMessage(diagnostic.message())
                .setLine(diagnostic.position().line())
                .setColumn(diagnostic.position().column())
                .build();
    }

    private Kind kind(final Diagnostic diagnostic) {
        return switch (diagnostic) {
            case Diagnostic.ParseError _ -> Kind.KIND_PARSE;
            case Diagnostic.LintError _ -> Kind.KIND_LINT;
            case Diagnostic.ConfigError _ -> Kind.KIND_CONFIG;
            case Diagnostic.InternalError _ -> Kind.KIND_INTERNAL;
            case Diagnostic.Warning _ -> Kind.KIND_WARNING;
        };
    }
}
