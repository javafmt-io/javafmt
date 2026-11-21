package io.javafmt.gradle.engine;

import io.javafmt.Diagnostic;
import io.javafmt.FileResult;
import io.javafmt.GitRatchet;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.SourceFiles;
import io.javafmt.cache.FormatCache;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class JavafmtEngine {

    public static Outcome run(final Request request) {
        Objects.requireNonNull(request, "request");
        final var root = request.projectDirectory().toPath();
        final var sources = request.files().stream()
                .map(File::toPath)
                .sorted()
                .toList();
        final var paths = request.ratchetFrom()
                .map(ref -> GitRatchet.since(ref, root).keep(sources))
                .orElse(sources);
        final var config = request.analysisClasspath()
                .map(classpath -> JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(classpath)))
                .orElseGet(JavafmtConfig::defaults);
        final var outcomes = outcomes(root, paths, config, request.threads(), request.write());
        return new Outcome(flatten(outcomes, Outcome::changed), flatten(outcomes, Outcome::errors), flatten(outcomes, Outcome::warnings));
    }

    private static <T> List<T> flatten(final List<Outcome> outcomes, final Function<Outcome, List<T>> part) {
        return outcomes.stream()
                .map(part)
                .flatMap(List::stream)
                .toList();
    }

    private static List<Outcome> outcomes(final Path root,
                                          final List<Path> paths,
                                          final JavafmtConfig config,
                                          final int threads,
                                          final boolean write) {
        try (final var results = Javafmt.formatFilesLazily(paths, config, FormatCache.noop(), threads)) {
            return results.map(result -> outcome(root, result, write)).toList();
        }
    }

    private static Outcome outcome(final Path root, final FileResult result, final boolean write) {
        return switch (result) {
            case FileResult.UpToDate _ -> new Outcome(List.of(), List.of(), List.of());
            case FileResult.Unreadable(var path, var error) ->
                new Outcome(List.of(), List.of(unreadable(source(root, path), "cannot read: " + error.getMessage())), List.of());
            case FileResult.Formatted formatted -> {
                if (write && formatted.changed()) {
                    SourceFiles.write(formatted.path(), formatted.result().output());
                }
                final var source = source(root, formatted.path());
                yield new Outcome(
                        formatted.changed() ? List.of(source) : List.of(),
                        findings(source, formatted.result().errors()),
                        findings(source, formatted.result().warnings()));
            }
        };
    }

    private static Outcome.Finding unreadable(final Outcome.Source source, final String message) {
        return new Outcome.Finding(source, 0, 0, message, source.display() + ": error: " + message);
    }

    private static List<Outcome.Finding> findings(final Outcome.Source source, final Collection<Diagnostic> diagnostics) {
        return diagnostics.stream()
                .map(diagnostic -> finding(source, diagnostic))
                .toList();
    }

    private static Outcome.Finding finding(final Outcome.Source source, final Diagnostic diagnostic) {
        return new Outcome.Finding(
                source, diagnostic.position().line(), diagnostic.position().column(), diagnostic.message(), diagnostic.describe(source.display()));
    }

    private static Outcome.Source source(final Path root, final Path path) {
        return new Outcome.Source(path.toAbsolutePath().toString(), path.startsWith(root)
                ? root.relativize(path).toString()
                : path.toString());
    }

    private JavafmtEngine() {}
}
