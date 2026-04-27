package io.github.jschneidereit.grind.cli;

import io.github.jschneidereit.grind.FormatResult;
import io.github.jschneidereit.grind.Grind;
import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.parser.ParseOutcome;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

public final class Cli {

    public static int run(final String[] args, final InputStream in, final PrintStream out, final PrintStream err) {
        final var parsed = parse(args);
        if (parsed.files.isEmpty()) {
            return runStdin(in, out, err);
        }
        return runFiles(parsed, err);
    }

    private static int runStdin(final InputStream in, final PrintStream out, final PrintStream err) {
        try {
            final var source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            final var result = Grind.formatWithResult(source);
            reportDiagnostics("<stdin>", result, err);
            out.print(result.output());
            return result.hasErrors() ? 1 : 0;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int runFiles(final Args parsed, final PrintStream err) {
        // Two-stage: batch-parse on this thread (one JavacTask amortizes initPlugins across
        // all files), then format-from-parsed in the pool (build/print is independent per
        // file). Read failures are recorded inline; their slot's source becomes "" so the
        // index alignment with parseUnits' result list is preserved.
        final var sources = new ArrayList<String>(parsed.files.size());
        final var readErrors = new ArrayList<@Nullable FileOutcome>(parsed.files.size());
        for (final var path : parsed.files) {
            try {
                sources.add(Files.readString(path, StandardCharsets.UTF_8));
                readErrors.add(null);
            } catch (final IOException e) {
                sources.add("");
                readErrors.add(new FileOutcome(2, path + ": read failed: " + e.getMessage()));
            }
        }
        final var outcomes = JavaParser.parseUnits(sources);

        final var threads = Math.max(1, parsed.threads);
        final var pool = Executors.newFixedThreadPool(threads, r -> {
            final var t = new Thread(r, "grind-fmt");
            t.setDaemon(true);
            return t;
        });
        try {
            final List<Future<FileOutcome>> futures = IntStream.range(0, parsed.files.size())
                .<Future<FileOutcome>>mapToObj(i -> {
                    final var readError = readErrors.get(i);
                    if (readError != null) {
                        return CompletableFuture.completedFuture(readError);
                    }
                    final var path = parsed.files.get(i);
                    final var source = sources.get(i);
                    final var outcome = outcomes.get(i);
                    return pool.submit(() -> formatOne(path, source, outcome, parsed.check));
                })
                .toList();
            var worstExit = 0;
            for (final var f : futures) {
                final var outcome = f.get();
                if (outcome.diagnostic() != null) {
                    err.println(outcome.diagnostic());
                }
                worstExit = Math.max(worstExit, outcome.exitCode());
            }
            return worstExit;
        } catch (final Exception e) {
            err.println("grind: " + e.getMessage());
            return 2;
        } finally {
            pool.shutdownNow();
        }
    }

    private static FileOutcome formatOne(final Path path, final String source, final ParseOutcome outcome, final boolean checkOnly) {
        final var result = Grind.formatWithResult(source, outcome);
        if (result.hasErrors()) {
            final var first = result.diagnostics().get(0);
            return new FileOutcome(1, path + ": " + first.message());
        }
        if (result.output().equals(source)) {
            return new FileOutcome(0, null);
        }
        if (checkOnly) {
            return new FileOutcome(1, path + ": would reformat");
        }
        try {
            Files.writeString(path, result.output(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            return new FileOutcome(2, path + ": write failed: " + e.getMessage());
        }
        return new FileOutcome(0, null);
    }

    private static void reportDiagnostics(final String name, final FormatResult result, final PrintStream err) {
        result.diagnostics().forEach(d -> err.println(
            name + ":" + d.position().line() + ":" + d.position().column()
                + ": " + (d.isError() ? "error" : "warning") + ": " + d.message()));
    }

    private record FileOutcome(int exitCode, @Nullable String diagnostic) {}

    private record Args(List<Path> files, boolean check, int threads) {}

    private static Args parse(final String[] args) {
        final var files = new ArrayList<Path>();
        var check = false;
        var threads = Runtime.getRuntime().availableProcessors();
        for (var i = 0; i < args.length; i++) {
            final var a = args[i];
            switch (a) {
                case "--check" -> check = true;
                case "--threads" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--threads requires a value");
                    }
                    threads = Integer.parseInt(args[++i]);
                }
                default -> {
                    if (a.startsWith("--")) {
                        throw new IllegalArgumentException("Unknown flag: " + a);
                    }
                    files.add(Paths.get(a));
                }
            }
        }
        return new Args(List.copyOf(files), check, threads);
    }

    private Cli() {}
}
