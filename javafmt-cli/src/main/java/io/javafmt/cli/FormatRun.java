package io.javafmt.cli;

import io.javafmt.FileResult;
import io.javafmt.FormatResult;
import io.javafmt.GitRatchet;
import io.javafmt.GitRatchetException;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.PathFilter;
import io.javafmt.ProjectFile;
import io.javafmt.SourceFiles;
import io.javafmt.cache.CacheKey;
import io.javafmt.cache.FileFormatCache;
import io.javafmt.cache.FormatCache;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

@UtilityClass
final class FormatRun {

    int runPaths(final Arguments parsed, final PrintStream err) {
        try {
            final var changed = changedFiles(parsed);
            final var expansion = expandDirectories(parsed, err);
            final var excluded = PathFilter.ofGlobs(parsed.excludes());
            final var exit = runFiles(
                    parsed.withFiles(expansion.files().stream()
                            .filter(not(excluded::excludes))
                            .filter(changed)
                            .toList()),
                    err);
            return Math.max(exit, expansion.unreadable() ? ExitCode.ERROR : ExitCode.OK);
        } catch (final GitRatchetException e) {
            final var message = Objects.requireNonNullElse(e.getMessage(), "git failed");
            err.println(parsed.diagnostics() == DiagnosticsFormat.JSON ? JsonDiagnostics.cliLine("", message) : "javafmt: " + message);
            return ExitCode.ERROR;
        }
    }

    private Predicate<Path> changedFiles(final Arguments parsed) {
        if (parsed.ratchetFrom().isEmpty()) {
            return _ -> true;
        }
        final var ratchets = parsed.files().stream()
                .map(FormatRun::gitDirectory)
                .distinct()
                .map(directory -> GitRatchet.since(parsed.ratchetFrom().get(), directory))
                .toList();
        return file -> ratchets.stream()
                .anyMatch(ratchet -> ratchet.includes(file));
    }

    private Path gitDirectory(final Path path) {
        final var absolute = path.toAbsolutePath();
        return Files.isDirectory(absolute) ? absolute : Objects.requireNonNullElse(absolute.getParent(), absolute);
    }

    /**
     * Formats stdin to stdout, or in check mode reports via exit code alone.
     *
     * <p>Outside check mode, on a parse/lint error, still emits the unchanged source alongside the stderr diagnostic so
     * a pipe stays lossless. In check mode stdout is the answer channel, not a place to write the
     * formatted source — printing it there would corrupt a {@code javafmt --check < F.java > out}
     * pipeline, so silence plus the exit code is the whole contract.
     */
    int runStdin(final Arguments parsed, final ProjectFile projectFile, final InputStream in, final PrintStream out, final PrintStream err) {
        try {
            final var source = StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(in.readAllBytes()))
                    .toString();
            final var result = Javafmt.formatWithResult(source, parsed.config(), projectFile);
            reportDiagnostics(parsed.diagnostics().stdinName(), result, parsed, err);
            if (result.hasErrors()) {
                if (!parsed.check()) {
                    out.print(result.output());
                }
                return ExitCode.FAILED;
            }
            if (parsed.check()) {
                return checkStdinAgainstSource(parsed, source, result, err);
            }
            out.print(result.output());
            return ExitCode.OK;
        } catch (final IOException e) {
            err.println(parsed.diagnostics() == DiagnosticsFormat.JSON
                    ? JsonDiagnostics.cliLine("", "read failed: " + describe(e))
                    : "javafmt: <stdin>: read failed: " + describe(e));
            return ExitCode.ERROR;
        }
    }

    private int checkStdinAgainstSource(final Arguments parsed, final String source, final FormatResult result, final PrintStream err) {
        if (result.output().equals(source)) {
            return ExitCode.OK;
        }
        err.println(parsed.diagnostics().fileMessage(parsed.diagnostics().stdinName(), "would reformat"));
        return ExitCode.FAILED;
    }

    /**
     * Replaces any directory argument with the {@code .java} files beneath it, which is what
     * {@code javafmt src/} has to mean — the mojos already walk source roots this way. A directory
     * that cannot be read is reported and its readable siblings are still formatted, but the run
     * fails: a CI gate that checked nothing must not look like a pass.
     */
    private Expansion expandDirectories(final Arguments parsed, final PrintStream err) {
        final var found = parsed.files().stream()
                .map(path -> Files.isDirectory(path) ? javaFilesUnder(path, parsed) : new SourceFiles.Discovery(List.of(path), List.of()))
                .toList();
        final var unreadable = found.stream()
                .flatMap(discovery -> discovery.unreadable().stream())
                .toList();
        unreadable.forEach(entry -> reportUnreadable(entry, parsed.diagnostics(), err));
        final var files = found.stream()
                .flatMap(discovery -> discovery.files().stream())
                .toList();
        return new Expansion(files, !unreadable.isEmpty());
    }

    private SourceFiles.Discovery javaFilesUnder(final Path dir, final Arguments parsed) {
        return parsed.defaultExcludes() ? SourceFiles.discover(dir) : SourceFiles.discoverWithoutDefaultExcludes(dir);
    }

    private void reportUnreadable(final SourceFiles.Unreadable unreadable, final DiagnosticsFormat format, final PrintStream err) {
        final var path = unreadable.path().toString();
        final var message = "cannot read directory: " + describe(unreadable.error());
        err.println(format == DiagnosticsFormat.JSON ? JsonDiagnostics.cliLine(path, message) : "javafmt: " + path + ": " + message);
    }

    private int runFiles(final Arguments parsed, final PrintStream err) {
        final var cache = parsed.cacheFile()
                .<FormatCache>map(file -> FileFormatCache.load(file, cacheKey(parsed)))
                .orElseGet(FormatCache::noop);
        final var outcomes = outcomesOf(parsed, cache);
        outcomes.stream()
                .flatMap(outcome -> outcome.report().stream())
                .forEach(err::println);
        final var worstExit = outcomes.stream()
                .mapToInt(FileOutcome::exitCode)
                .max()
                .orElse(ExitCode.OK);
        if (worstExit <= ExitCode.FAILED) {
            persistCache(cache, parsed.diagnostics(), err);
        }
        return worstExit;
    }

    private List<FileOutcome> outcomesOf(final Arguments parsed, final FormatCache cache) {
        try (
                final var files = Javafmt.formatFilesLazily(parsed.files(), parsed.config(), cache, parsed.threads());
                final var pool = Executors.newFixedThreadPool(parsed.threads(), Thread.ofPlatform()
                        .name("javafmt-write-", 0)
                        .daemon(true)
                        .factory())) {
            return files.map(file -> CompletableFuture.supplyAsync(() -> outcomeFor(file, parsed, cache), pool))
                    .toList()
                    .stream()
                    .map(FormatRun::joined)
                    .toList();
        }
    }

    private <T> T joined(final CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (final CompletionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    /**
     * The configuration a cache entry was written under. The classpath and the project files that
     * govern the files change what a file formats to, so entries from a run without them must not
     * be reused by one with them.
     */
    private CacheKey cacheKey(final Arguments parsed) {
        return CacheKey.of(parsed.config(), ProjectFile.forFiles(parsed.files()));
    }

    /**
     * Persists the cache even on a check-mode "would reformat" exit (1) so already-clean files in
     * the same batch are recorded. The caller skips this on hard errors (exit &gt;= 2), where the
     * run was incomplete.
     */
    private void persistCache(final FormatCache cache, final DiagnosticsFormat format, final PrintStream err) {
        try {
            cache.save();
        } catch (final RuntimeException e) {
            err.println(format == DiagnosticsFormat.JSON
                    ? JsonDiagnostics.cliLine("", "failed to save cache: " + e.getMessage())
                    : "javafmt: warning: failed to save cache: " + e.getMessage());
        }
    }

    private FileOutcome outcomeFor(final FileResult file, final Arguments parsed, final FormatCache cache) {
        return switch (file) {
            case FileResult.UpToDate _ -> new FileOutcome(ExitCode.OK, List.of());
            case FileResult.Unreadable(var path, var error) ->
                new FileOutcome(ExitCode.ERROR, List.of(parsed.diagnostics().fileMessage(path.toString(), "read failed: " + describe(error))));
            case FileResult.Formatted formatted -> outcomeOf(formatted, parsed, cache);
        };
    }

    private FileOutcome outcomeOf(final FileResult.Formatted formatted, final Arguments parsed, final FormatCache cache) {
        final var path = formatted.path();
        final var result = formatted.result();
        final var format = parsed.diagnostics();
        final var reported = result.diagnostics().stream()
                .map(d -> format.diagnostic(path.toString(), d))
                .toList();
        if (result.hasErrors()) {
            return new FileOutcome(ExitCode.FAILED, reported);
        }
        if (!formatted.changed()) {
            cache.recordFormatted(path, formatted.source());
            return new FileOutcome(ExitCode.OK, reported);
        }
        if (parsed.check()) {
            return new FileOutcome(ExitCode.FAILED, append(reported, format.fileMessage(path.toString(), "would reformat")));
        }
        try {
            SourceFiles.write(path, result.output());
        } catch (final UncheckedIOException e) {
            final var message = "write failed: " + describe(Objects.requireNonNull(e.getCause(), "cause"));
            return new FileOutcome(ExitCode.ERROR, append(reported, format.fileMessage(path.toString(), message)));
        }
        cache.recordFormatted(path, result.output());
        return new FileOutcome(ExitCode.OK, reported);
    }

    /**
     * What an I/O failure means in words. The exception's own message is the path repeated for a
     * permission failure and a byte count for an encoding failure, neither of which tells the user
     * what went wrong.
     */
    private String describe(final IOException e) {
        return switch (e) {
            case CharacterCodingException _ -> "not valid UTF-8";
            case AccessDeniedException _ -> "permission denied";
            case NoSuchFileException _ -> "no such file";
            default -> e.getMessage() != null ? e.getMessage() : e.toString();
        };
    }

    private List<String> append(final List<String> lines, final String line) {
        return Stream.concat(lines.stream(), Stream.of(line)).toList();
    }

    private void reportDiagnostics(final String name, final FormatResult result, final Arguments parsed, final PrintStream err) {
        result.diagnostics().forEach(d -> err.println(parsed.diagnostics().diagnostic(name, d)));
    }

    /** A directory argument replaced by its contents, and whether anything under it was unreadable. */
    private record Expansion(List<Path> files, boolean unreadable) {}

    /** One file's exit code and every line the run should print about it, in order. */
    private record FileOutcome(int exitCode, List<String> report) {}

    record Arguments(List<Path> files,
                     boolean check,
                     int threads,
                     Optional<Path> cacheFile,
                     List<String> excludes,
                     boolean defaultExcludes,
                     Optional<String> classpath,
                     Optional<String> ratchetFrom,
                     DiagnosticsFormat diagnostics) {

        Arguments {
            if (threads <= 0) {
                throw new IllegalArgumentException("threads must be positive, but was: " + threads);
            }
            files = List.copyOf(files);
            excludes = List.copyOf(excludes);
        }

        JavafmtConfig config() {
            return JavafmtConfig.defaults()
                    .withAnalysis(classpath.<JavafmtConfig.Analysis>map(JavafmtConfig.Analysis.Attributed::new)
                            .orElseGet(JavafmtConfig.Analysis.ParseOnly::new));
        }

        Arguments withFiles(final List<Path> files) {
            return new Arguments(files, check, threads, cacheFile, excludes, defaultExcludes, classpath, ratchetFrom, diagnostics);
        }
    }
}
