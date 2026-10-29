package io.javafmt.cli;

import io.javafmt.Diagnostic;
import io.javafmt.FormatResult;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.JavafmtVersion;
import io.javafmt.PathFilter;
import io.javafmt.SourceFiles;
import io.javafmt.cache.CacheKey;
import io.javafmt.cache.FileFormatCache;
import io.javafmt.cache.FormatCache;
import java.io.Console;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

@UtilityClass
public final class Cli {

    private final String USAGE = """
            Usage: javafmt [options] [file|directory ...]

            Formats Java source in place. With no file arguments, reads from stdin and writes the
            result to stdout. Directory arguments are searched recursively for .java files.

            Options:
              --check                 Do not write anything; exit 1 if any file would be reformatted.
              --diagnostics=<fmt>     Diagnostic format: 'text' (default) or 'json' (NDJSON on stderr).
              --exclude <glob>        Skip paths matching the glob. Repeatable.
              --no-default-excludes   Search build, target, out, generated, node_modules and .git too.
              --threads <n>           Files to format concurrently. Defaults to the CPU count.
              --cache <path>          Skip files unchanged since the last run recorded in this file.
              --analyze               Enable lint rules that need resolved types. Requires a classpath.
              --classpath <cp>        Classpath used by --analyze.
              --classpath-file <path> Read the --analyze classpath from a file.
              -                       Read from stdin, as with no file arguments at all.
              --                      Treat every following argument as a path, not a flag.
              -h, --help              Show this message.
              --version               Show the javafmt version.

            Exit codes:
              0  nothing to do
              1  a file would be reformatted, or could not be formatted
              2  bad usage, or a file could not be read or written
            """;

    public int run(final String[] args, final InputStream in, final PrintStream out, final PrintStream err) {
        return run(args, in, out, err, stdinIsATerminal());
    }

    /**
     * @param stdinIsATerminal whether a person is typing at the other end, in which case falling
     *     back to stdin would hang with no prompt; the tests pass it explicitly because a test
     *     harness never has a terminal.
     */
    int run(final String[] args, final InputStream in, final PrintStream out, final PrintStream err, final boolean stdinIsATerminal) {
        final Args parsed;
        try {
            parsed = parse(args);
        } catch (IllegalArgumentException e) {
            err.println("javafmt: " + e.getMessage());
            return 2;
        }
        if (parsed.help) {
            out.print(USAGE);
            return 0;
        }
        if (parsed.version) {
            out.println("javafmt " + JavafmtVersion.VERSION);
            return 0;
        }
        if (parsed.files.isEmpty()) {
            if (stdinIsATerminal && !parsed.stdin) {
                err.print(USAGE);
                return 2;
            }
            return runStdin(parsed, in, out, err);
        }
        final var expansion = expandDirectories(parsed, err);
        final var exit = runFiles(dropExcludedFiles(expansion.args()), err);
        return Math.max(exit, expansion.unreadable() ? 2 : 0);
    }

    private boolean stdinIsATerminal() {
        return Optional.ofNullable(System.console()).map(Console::isTerminal).orElse(false);
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
    private int runStdin(final Args parsed, final InputStream in, final PrintStream out, final PrintStream err) {
        try {
            final var source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            final var config = JavafmtConfig.defaults();
            final var result = parsed.analyze
                    ? Javafmt.formatWithAnalyze(source, parsed.classpath.orElseThrow(), config)
                    : Javafmt.formatWithResult(source, config);
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

    /**
     * Reads files and checks the cache here; everything from parsing onward is {@link
     * Javafmt#formatAll}'s job, which batches javac task setup across the run. A file already
     * resolved — a cache hit, or a read that failed — contributes {@code ""} as its format input
     * (see {@link FileWork#formatInput()}) so the result list stays index-aligned with {@code
     * parsed.files} without occupying a parse slot.
     */
    private int runFiles(final Args parsed, final PrintStream err) {
        final var cache = parsed.cacheFile.<FormatCache>map(file -> FileFormatCache.load(file, cacheKey(parsed))).orElseGet(FormatCache::noop);
        final var works = parsed.files.stream()
                .map(path -> readWork(path, cache, parsed.jsonDiagnostics()))
                .toList();
        final var toFormat = works.stream().map(FileWork::formatInput).toList();
        final var results = parsed.analyze
                ? Javafmt.formatAllWithAnalyze(toFormat, parsed.classpath.orElseThrow(), JavafmtConfig.defaults(), parsed.threads)
                : Javafmt.formatAll(toFormat, JavafmtConfig.defaults(), parsed.threads);
        final var outcomes = IntStream.range(0, works.size())
                .mapToObj(i -> outcomeFor(works.get(i), results.get(i), parsed.check, cache, parsed.jsonDiagnostics()))
                .toList();
        outcomes.stream()
                .flatMap(outcome -> outcome.report().stream())
                .forEach(err::println);
        final var worstExit = outcomes.stream().mapToInt(FileOutcome::exitCode).max().orElse(0);
        if (worstExit <= 1) {
            persistCache(cache, parsed.jsonDiagnostics(), err);
        }
        return worstExit;
    }

    /**
     * The configuration a cache entry was written under. {@code --analyze} and its classpath
     * change what a file formats to, so entries from a plain run must not be reused by one.
     */
    private CacheKey cacheKey(final Args parsed) {
        final var config = JavafmtConfig.defaults();
        return parsed.analyze ? CacheKey.ofAnalysis(config, parsed.classpath.orElseThrow()) : CacheKey.of(config);
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

    private FileWork readWork(final Path path, final FormatCache cache, final boolean json) {
        try {
            final var content = Files.readString(path, StandardCharsets.UTF_8);
            return new FileWork(path, content, null, cache.isUpToDate(path, content));
        } catch (IOException e) {
            final var message = "read failed: " + describe(e);
            return new FileWork(path, "", new FileOutcome(2, List.of(cliMessage(path, message, json))), false);
        }
    }

    private FileOutcome outcomeFor(final FileWork work, final FormatResult result, final boolean check, final FormatCache cache, final boolean json) {
        if (work.readError() != null) {
            return work.readError();
        }
        if (work.cacheHit()) {
            return new FileOutcome(0, List.of());
        }
        return finalizeFormatOutcome(work.path(), work.source(), result, check, cache, json);
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

    private Args parse(final String[] args) {
        final var files = new ArrayList<Path>();
        final var excludes = new ArrayList<String>();
        var check = false;
        var defaultExcludes = true;
        var stdin = false;
        var pathsOnly = false;
        var threads = Runtime.getRuntime().availableProcessors();
        var cacheFile = Optional.<Path>empty();
        var analyze = false;
        var classpath = Optional.<String>empty();
        var jsonDiagnostics = false;
        var help = false;
        var version = false;
        for (var i = 0; i < args.length; i++) {
            final var a = args[i];
            if (pathsOnly) {
                files.add(Path.of(a));
                continue;
            }
            switch (a) {
                case "--" -> pathsOnly = true;
                case "-" -> stdin = true;
                case "--check" -> check = true;
                case "--no-default-excludes" -> defaultExcludes = false;
                case "--help", "-h" -> help = true;
                case "--version" -> version = true;
                case "--analyze" -> analyze = true;
                case "--threads" -> {
                    final var raw = flagValue(args, i, "--threads", "a positive integer");
                    i++;
                    try {
                        threads = Integer.parseInt(raw);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("--threads requires a positive integer, got: " + raw, e);
                    }
                    if (threads <= 0) {
                        throw new IllegalArgumentException("--threads requires a positive integer, got: " + threads);
                    }
                }
                case "--cache" -> {
                    final var raw = flagValue(args, i, "--cache", "a path argument");
                    i++;
                    cacheFile = Optional.of(Path.of(raw));
                }
                case "--classpath" -> {
                    final var raw = flagValue(args, i, "--classpath", "a value");
                    i++;
                    classpath = Optional.of(raw);
                }
                case "--classpath-file" -> {
                    final var raw = flagValue(args, i, "--classpath-file", "a path argument");
                    i++;
                    final var cpFile = Path.of(raw);
                    try {
                        classpath = Optional.of(Files.readString(cpFile, StandardCharsets.UTF_8).trim());
                    } catch (IOException e) {
                        throw new IllegalArgumentException("--classpath-file: cannot read " + cpFile + ": " + e.getMessage(), e);
                    }
                }
                case "--exclude" -> {
                    final var raw = flagValue(args, i, "--exclude", "a glob pattern");
                    i++;
                    excludes.add(raw);
                }
                default -> {
                    if (a.startsWith("--diagnostics=")) {
                        final var value = a.substring("--diagnostics=".length());
                        jsonDiagnostics = switch (value) {
                            case "json" -> true;
                            case "text" -> false;
                            default -> throw new IllegalArgumentException("--diagnostics must be 'json' or 'text', not: " + value);
                        };
                    } else if (a.startsWith("-")) {
                        throw new IllegalArgumentException("unknown flag: " + a);
                    } else {
                        files.add(Path.of(a));
                    }
                }
            }
        }
        if (stdin && !files.isEmpty()) {
            throw new IllegalArgumentException("'-' reads stdin and cannot be combined with file arguments");
        }
        if (!help && !version && analyze && classpath.isEmpty()) {
            throw new IllegalArgumentException("--analyze requires --classpath or --classpath-file; pass one or drop --analyze");
        }
        final var paths = List.copyOf(files);
        final var globs = List.copyOf(excludes);
        return new Args(paths, check, threads, cacheFile, globs, defaultExcludes, stdin, analyze, classpath, jsonDiagnostics, help, version);
    }

    private String flagValue(final String[] args, final int i, final String flag, final String expectation) {
        if (i + 1 >= args.length) {
            throw new IllegalArgumentException(flag + " requires " + expectation);
        }
        return args[i + 1];
    }

    /** A directory argument replaced by its contents, and whether anything under it was unreadable. */
    private record Expansion(Args args, boolean unreadable) {}

    /** One file's exit code and every line the run should print about it, in order. */
    private record FileOutcome(int exitCode, List<String> report) {}

    private record FileWork(Path path, String source, @Nullable FileOutcome readError, boolean cacheHit) {

        String formatInput() {
            return readError == null && !cacheHit ? source : "";
        }
    }

    private record Args(
            List<Path> files,
            boolean check,
            int threads,
            Optional<Path> cacheFile,
            List<String> excludes,
            boolean defaultExcludes,
            boolean stdin,
            boolean analyze,
            Optional<String> classpath,
            boolean jsonDiagnostics,
            boolean help,
            boolean version) {

        Args withFiles(final List<Path> files) {
            return new Args(files, check, threads, cacheFile, excludes, defaultExcludes, stdin, analyze, classpath, jsonDiagnostics, help, version);
        }
    }
}
