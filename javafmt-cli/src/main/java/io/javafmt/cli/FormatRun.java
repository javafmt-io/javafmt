package io.javafmt.cli;

import io.javafmt.Diagnostic;
import io.javafmt.FileResult;
import io.javafmt.FormatResult;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.PathFilter;
import io.javafmt.SourceFiles;
import io.javafmt.cache.CacheKey;
import io.javafmt.cache.FileFormatCache;
import io.javafmt.cache.FormatCache;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
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
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class FormatRun {

    int runPaths(final Args parsed, final PrintStream err) {
        final var expansion = expandDirectories(parsed, err);
        final var exit = runFiles(dropExcludedFiles(expansion.args()), err);
        return Math.max(exit, expansion.unreadable() ? 2 : 0);
    }

    private Args dropExcludedFiles(final Args parsed) {
        if (parsed.excludes.isEmpty()) {
            return parsed;
        }
        final var filter = PathFilter.ofGlobs(parsed.excludes);
        return parsed.withFiles(filter.keep(parsed.files));
    }

    /**
     * Formats stdin to stdout, or in check mode reports via exit code alone.
     *
     * <p>On a parse/lint error, still emits the unchanged source alongside the stderr diagnostic so
     * a pipe stays lossless. In check mode stdout is the answer channel, not a place to write the
     * formatted source — printing it there would corrupt a {@code javafmt --check < F.java > out}
     * pipeline, so silence plus the exit code is the whole contract.
     */
    int runStdin(final Args parsed, final InputStream in, final PrintStream out, final PrintStream err) {
        try {
            final var source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            final var result = Javafmt.formatWithResult(source, parsed.config());
            reportDiagnostics(parsed.jsonDiagnostics() ? "" : "<stdin>", result, parsed, err);
            if (result.hasErrors()) {
                out.print(result.output());
                return 1;
            }
            if (parsed.check) {
                return checkStdinAgainstSource(parsed, source, result, err);
            }
            out.print(result.output());
            return 0;
        } catch (IOException e) {
            err.println(
                    parsed.jsonDiagnostics()
                            ? JsonDiagnostics.cliLine("", "read failed: " + describe(e))
                            : "javafmt: <stdin>: read failed: " + describe(e));
            return 2;
        }
    }

    private int checkStdinAgainstSource(final Args parsed, final String source, final FormatResult result, final PrintStream err) {
        if (result.output().equals(source)) {
            return 0;
        }
        err.println(parsed.jsonDiagnostics() ? JsonDiagnostics.cliLine("", "would reformat") : "<stdin>: would reformat");
        return 1;
    }

    /**
     * Replaces any directory argument with the {@code .java} files beneath it, which is what
     * {@code javafmt src/} has to mean — the mojos already walk source roots this way. A directory
     * that cannot be read is reported and its readable siblings are still formatted, but the run
     * fails: a CI gate that checked nothing must not look like a pass.
     */
    private Expansion expandDirectories(final Args parsed, final PrintStream err) {
        if (parsed.files.stream().noneMatch(Files::isDirectory)) {
            return new Expansion(parsed, false);
        }
        final var found = parsed.files.stream()
                .map(path -> Files.isDirectory(path) ? javaFilesUnder(path, parsed) : new SourceFiles.Discovery(List.of(path), List.of()))
                .toList();
        found.stream()
                .flatMap(discovery -> discovery.unreadable().stream())
                .forEach(u -> reportUnreadable(u, parsed.jsonDiagnostics(), err));
        final var expanded = found.stream().flatMap(discovery -> discovery.files().stream()).toList();
        return new Expansion(parsed.withFiles(expanded), found.stream().anyMatch(d -> !d.unreadable().isEmpty()));
    }

    private SourceFiles.Discovery javaFilesUnder(final Path dir, final Args parsed) {
        return parsed.defaultExcludes ? SourceFiles.discover(dir) : SourceFiles.discoverWithoutDefaultExcludes(dir);
    }

    private void reportUnreadable(final SourceFiles.Unreadable unreadable, final boolean json, final PrintStream err) {
        final var path = unreadable.path().toString();
        final var message = "cannot read directory: " + describe(unreadable.error());
        err.println(json ? JsonDiagnostics.cliLine(path, message) : "javafmt: " + path + ": " + message);
    }

    private int runFiles(final Args parsed, final PrintStream err) {
        final var cache = parsed.cacheFile.<FormatCache>map(file -> FileFormatCache.load(file, cacheKey(parsed))).orElseGet(FormatCache::noop);
        final var outcomes = outcomesOf(Javafmt.formatFiles(parsed.files, parsed.config(), cache, parsed.threads), parsed, cache);
        outcomes.stream()
                .flatMap(outcome -> outcome.report().stream())
                .forEach(err::println);
        final var worstExit = outcomes.stream().mapToInt(FileOutcome::exitCode).max().orElse(0);
        if (worstExit <= 1) {
            persistCache(cache, parsed.jsonDiagnostics(), err);
        }
        return worstExit;
    }

    private List<FileOutcome> outcomesOf(final List<FileResult> files, final Args parsed, final FormatCache cache) {
        try (
                final var pool = Executors.newFixedThreadPool(
                        Math.max(1, parsed.threads),
                        Thread.ofPlatform().name("javafmt-write-", 0).daemon(true).factory())) {
            return files.stream()
                    .map(file -> CompletableFuture.supplyAsync(() -> outcomeFor(file, parsed.check, cache, parsed.jsonDiagnostics()), pool))
                    .toList()
                    .stream()
                    .map(CompletableFuture::join)
                    .toList();
        }
    }

    /**
     * The configuration a cache entry was written under. {@code --analyze} and its classpath
     * change what a file formats to, so entries from a plain run must not be reused by one.
     */
    private CacheKey cacheKey(final Args parsed) {
        return CacheKey.of(parsed.config());
    }

    /**
     * Persists the cache even on a check-mode "would reformat" exit (1) so already-clean files in
     * the same batch are recorded. The caller skips this on hard errors (exit &gt;= 2), where the
     * run was incomplete.
     */
    private void persistCache(final FormatCache cache, final boolean jsonDiagnostics, final PrintStream err) {
        try {
            cache.save();
        } catch (RuntimeException e) {
            err.println(
                    jsonDiagnostics
                            ? JsonDiagnostics.cliLine("", "failed to save cache: " + e.getMessage())
                            : "javafmt: warning: failed to save cache: " + e.getMessage());
        }
    }

    private FileOutcome outcomeFor(final FileResult file, final boolean check, final FormatCache cache, final boolean json) {
        return switch (file) {
            case FileResult.UpToDate ignored -> new FileOutcome(0, List.of());
            case FileResult.Unreadable(var path, var error) -> new FileOutcome(2, List.of(cliMessage(path, "read failed: " + describe(error), json)));
            case FileResult.Formatted(var path, var source, var result) -> finalizeFormatOutcome(path, source, result, check, cache, json);
        };
    }

    private FileOutcome finalizeFormatOutcome(
            final Path path,
            final String source,
            final FormatResult result,
            final boolean checkOnly,
            final FormatCache cache,
            final boolean json) {
        final var reported = result.diagnostics()
                .stream()
                .map(d -> json ? JsonDiagnostics.line(path.toString(), d) : d.describe(path.toString()))
                .toList();
        if (result.hasErrors()) {
            return new FileOutcome(1, reported);
        }
        if (result.output().equals(source)) {
            cache.recordFormatted(path, source);
            return new FileOutcome(0, reported);
        }
        if (checkOnly) {
            return new FileOutcome(1, append(reported, cliMessage(path, "would reformat", json)));
        }
        try {
            SourceFiles.write(path, result.output());
        } catch (UncheckedIOException e) {
            final var message = "write failed: " + describe(Objects.requireNonNull(e.getCause(), "cause"));
            return new FileOutcome(2, append(reported, cliMessage(path, message, json)));
        }
        cache.recordFormatted(path, result.output());
        return new FileOutcome(0, reported);
    }

    /**
     * What an I/O failure means in words. The exception's own message is the path repeated for a
     * permission failure and a byte count for an encoding failure, neither of which tells the user
     * what went wrong.
     */
    private String describe(final IOException e) {
        return switch (e) {
            case CharacterCodingException ignored -> "not valid UTF-8";
            case AccessDeniedException ignored -> "permission denied";
            case NoSuchFileException ignored -> "no such file";
            default -> e.getMessage() != null ? e.getMessage() : e.toString();
        };
    }

    /** javafmt's own word about a file — not a {@link Diagnostic}, so it carries no position. */
    private String cliMessage(final Path path, final String message, final boolean json) {
        return json ? JsonDiagnostics.cliLine(path.toString(), message) : path + ": " + message;
    }

    private List<String> append(final List<String> lines, final String line) {
        return Stream.concat(lines.stream(), Stream.of(line)).toList();
    }

    private void reportDiagnostics(final String name, final FormatResult result, final Args parsed, final PrintStream err) {
        result.diagnostics()
                .forEach(d -> err.println(parsed.jsonDiagnostics() ? JsonDiagnostics.line(name, d) : d.describe(name)));
    }

    /** A directory argument replaced by its contents, and whether anything under it was unreadable. */
    private record Expansion(Args args, boolean unreadable) {}

    /** One file's exit code and every line the run should print about it, in order. */
    private record FileOutcome(int exitCode, List<String> report) {}

    record Args(
            List<Path> files,
            boolean check,
            int threads,
            Optional<Path> cacheFile,
            List<String> excludes,
            boolean defaultExcludes,
            Optional<String> classpath,
            boolean jsonDiagnostics) {

        Args {
            files = List.copyOf(files);
            excludes = List.copyOf(excludes);
        }

        JavafmtConfig config() {
            return JavafmtConfig.defaults()
                    .withAnalysis(
                            classpath.<JavafmtConfig.Analysis>map(JavafmtConfig.Analysis.Attributed::new)
                                    .orElseGet(JavafmtConfig.Analysis.ParseOnly::new));
        }

        Args withFiles(final List<Path> files) {
            return new Args(files, check, threads, cacheFile, excludes, defaultExcludes, classpath, jsonDiagnostics);
        }
    }
}
