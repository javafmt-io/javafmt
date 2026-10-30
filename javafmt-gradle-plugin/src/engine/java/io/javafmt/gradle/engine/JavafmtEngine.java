package io.javafmt.gradle.engine;

import io.javafmt.Diagnostic;
import io.javafmt.FileResult;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JavafmtEngine {

    public static Outcome run(final Request request) {
        Objects.requireNonNull(request, "request");
        final var paths = request.files().stream().map(File::toPath).sorted().toList();
        final var threads = request.threads();
        final var results = request.analysisClasspath()
                .map(
                        classpath -> withParseOnlyFallback(
                                Javafmt.formatFiles(
                                        paths,
                                        JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(classpath)),
                                        FormatCache.noop(),
                                        threads),
                                threads))
                .orElseGet(() -> Javafmt.formatFiles(paths, JavafmtConfig.defaults(), FormatCache.noop(), threads));
        final var root = request.projectDirectory().toPath();
        final var formatted = results.stream()
                .flatMap(result -> result instanceof FileResult.Formatted f ? Stream.of(f) : Stream.empty())
                .toList();
        final var changed = formatted.stream().filter(FileResult.Formatted::changed).toList();
        if (request.write()) {
            changed.forEach(file -> SourceFiles.write(file.path(), file.result().output()));
        }
        return new Outcome(
                changed.stream().map(file -> source(root, file.path())).toList(),
                Stream.concat(
                        results.stream()
                                .flatMap(result -> result instanceof FileResult.Unreadable u ? Stream.of(u) : Stream.empty())
                                .map(u -> unreadable(source(root, u.path()), "cannot read: " + u.error().getMessage())),
                        findings(root, formatted, file -> file.result().errors()))
                        .toList(),
                findings(root, formatted, file -> file.result().warnings()).toList());
    }

    private static Outcome.Finding unreadable(final Outcome.Source source, final String message) {
        return new Outcome.Finding(source, 0, 0, message, source.display() + ": error: " + message);
    }

    private static List<FileResult> withParseOnlyFallback(final List<FileResult> attributed, final int threads) {
        final var failed = attributed.stream()
                .filter(result -> result instanceof FileResult.Formatted f && f.result().hasErrors())
                .map(FileResult::path)
                .toList();
        if (failed.isEmpty()) {
            return attributed;
        }
        final var retried = Javafmt.formatFiles(failed, JavafmtConfig.defaults(), FormatCache.noop(), threads)
                .stream()
                .collect(Collectors.toMap(FileResult::path, Function.identity()));
        return attributed.stream().map(result -> retried.getOrDefault(result.path(), result)).toList();
    }

    private static Stream<Outcome.Finding> findings(
            final Path root,
            final List<FileResult.Formatted> formatted,
            final Function<FileResult.Formatted, Collection<Diagnostic>> diagnostics) {
        return formatted.stream()
                .flatMap(file -> diagnostics.apply(file).stream().map(diagnostic -> finding(source(root, file.path()), diagnostic)));
    }

    private static Outcome.Finding finding(final Outcome.Source source, final Diagnostic diagnostic) {
        return new Outcome.Finding(
                source,
                diagnostic.position().line(),
                diagnostic.position().column(),
                diagnostic.message(),
                diagnostic.describe(source.display()));
    }

    private static Outcome.Source source(final Path root, final Path path) {
        return new Outcome.Source(path.toAbsolutePath().toString(), path.startsWith(root) ? root.relativize(path).toString() : path.toString());
    }

    private JavafmtEngine() {}
}
