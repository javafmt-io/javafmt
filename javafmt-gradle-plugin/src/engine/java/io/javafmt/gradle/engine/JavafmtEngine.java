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
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

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
        final var threads = request.threads();
        final var config = request.analysisClasspath()
                .map(classpath -> JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(classpath)))
                .orElseGet(JavafmtConfig::defaults);
        final var summaries = summaries(root, paths, config, threads, request.write());
        return new Outcome(
                summaries.stream()
                        .flatMap(summary -> summary.changed().stream())
                        .toList(),
                summaries.stream()
                        .flatMap(summary -> summary.errors().stream())
                        .toList(),
                summaries.stream()
                        .flatMap(summary -> summary.warnings().stream())
                        .toList());
    }

    private static List<Summary> summaries(final Path root,
                                           final List<Path> paths,
                                           final JavafmtConfig config,
                                           final int threads,
                                           final boolean write) {
        try (final var results = Javafmt.formatFilesLazily(paths, config, FormatCache.noop(), threads)) {
            return results.map(result -> summary(root, result, write)).toList();
        }
    }

    private static Summary summary(final Path root, final FileResult result, final boolean write) {
        return switch (result) {
            case FileResult.UpToDate _ -> new Summary(Optional.empty(), List.of(), List.of());
            case FileResult.Unreadable(var path, var error) ->
                new Summary(Optional.empty(), List.of(unreadable(source(root, path), "cannot read: " + error.getMessage())), List.of());
            case FileResult.Formatted formatted -> {
                if (write && formatted.changed()) {
                    SourceFiles.write(formatted.path(), formatted.result().output());
                }
                yield new Summary(
                        formatted.changed() ? Optional.of(source(root, formatted.path())) : Optional.empty(),
                        findings(root, formatted, file -> file.result().errors())
                                .toList(),
                        findings(root, formatted, file -> file.result().warnings())
                                .toList());
            }
        };
    }

    private static Outcome.Finding unreadable(final Outcome.Source source, final String message) {
        return new Outcome.Finding(source, 0, 0, message, source.display() + ": error: " + message);
    }

    private static Stream<Outcome.Finding> findings(final Path root,
                                                    final FileResult.Formatted file,
                                                    final Function<FileResult.Formatted, Collection<Diagnostic>> diagnostics) {
        return diagnostics.apply(file).stream()
                .map(diagnostic -> finding(source(root, file.path()), diagnostic));
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

    private record Summary(Optional<Outcome.Source> changed, List<Outcome.Finding> errors, List<Outcome.Finding> warnings) {}

    private JavafmtEngine() {}
}
