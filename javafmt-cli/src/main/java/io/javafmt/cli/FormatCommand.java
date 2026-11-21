package io.javafmt.cli;

import io.javafmt.JavafmtVersion;
import io.javafmt.PathFilter;
import io.javafmt.ProjectFile;
import io.javafmt.daemon.Daemon;
import io.javafmt.daemon.Wire;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

@Command(
        name = "javafmt",
        mixinStandardHelpOptions = true,
        version = "javafmt " + JavafmtVersion.VERSION,
        showAtFileInUsageHelp = true,
        showEndOfOptionsDelimiterInUsageHelp = true,
        description = {
                "Formats Java source in place. With no paths, or with '-', reads from stdin and writes the result to stdout.",
                "Directories are searched recursively for .java files.",
        },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
                "0:nothing to do",
                "1:a file would be reformatted, or could not be formatted",
                "2:bad usage, a file could not be read or written, or javafmt failed or ran out of memory",
        })
final class FormatCommand implements Callable<Integer> {

    private static final Path STDIN = Path.of("-");

    private final InputStream in;

    private final PrintStream out;

    private final PrintStream err;

    private final boolean stdinIsATerminal;

    private final Path workingDirectory;

    @Spec
    private CommandSpec spec;

    @Option(names = "--check", description = "Do not write anything; exit 1 if any file would be reformatted.")
    private boolean check;

    @Option(
            names = "--daemon",
            description =
                    "Serve length-delimited protobuf format requests from stdin until it closes, one reply each on stdout. For editor integrations.")
    private boolean daemon;

    @Option(names = "--json", description = "With --daemon, speak protobuf's JSON mapping instead, one message per line. For debugging.")
    private boolean json;

    @Option(
            names = "--diagnostics",
            paramLabel = "<format>",
            defaultValue = "text",
            description = "Diagnostic format on stderr: text, or json for NDJSON. Default: ${DEFAULT-VALUE}.")
    private DiagnosticsFormat diagnostics = DiagnosticsFormat.TEXT;

    private List<String> excludes = List.of();

    @Option(names = "--no-default-excludes", description = "Search build, target, out, generated, node_modules and .git too.")
    private boolean noDefaultExcludes;

    private OptionalInt threads = OptionalInt.empty();

    @Option(
            names = "--ratchet-from",
            paramLabel = "<ref>",
            description = "Only format files that differ from where HEAD branched off this git ref: changed, staged and untracked files.")
    private Optional<String> ratchetFrom = Optional.empty();

    @Option(names = "--cache", paramLabel = "<file>", description = "Skip files unchanged since the last run recorded in this file.")
    private Optional<Path> cache = Optional.empty();

    @Option(
            names = "--classpath",
            paramLabel = "<classpath>",
            description =
                    "Compile classpath of the sources, which turns on the lint rules that need resolved types. Pass @<file> to read it from a file.")
    private Optional<String> classpath = Optional.empty();

    @Option(names = "--no-analyze", description = "Leave the lint rules that need resolved types off, even with --classpath.")
    private boolean noAnalyze;

    @Parameters(paramLabel = "<path>", description = "Files or directories to format, or '-' for stdin.")
    private List<Path> paths = new ArrayList<>();

    FormatCommand(final InputStream in, final PrintStream out, final PrintStream err, final boolean stdinIsATerminal, final Path workingDirectory) {
        this.in = in;
        this.out = out;
        this.err = err;
        this.stdinIsATerminal = stdinIsATerminal;
        this.workingDirectory = workingDirectory;
    }

    @Option(names = "--exclude", paramLabel = "<glob>", description = "Skip paths matching the glob. Repeatable.")
    void setExcludes(final List<String> globs) {
        globs.forEach(glob -> {
            try {
                PathFilter.ofGlobs(List.of(glob));
            } catch (final PatternSyntaxException e) {
                throw new ParameterException(spec.commandLine(), "--exclude is not a valid glob: " + glob + " (" + e.getDescription() + ")", e);
            }
        });
        this.excludes = List.copyOf(globs);
    }

    @Option(
            names = "--threads",
            paramLabel = "<n>",
            description = "Files, or with --daemon requests, to format concurrently. Default: the CPU count, or for --daemon the CPU count up to 4.")
    void setThreads(final int threads) {
        if (threads <= 0) {
            throw new ParameterException(spec.commandLine(), "--threads must be a positive integer, but was: " + threads);
        }
        this.threads = OptionalInt.of(threads);
    }

    @Override
    public Integer call() {
        if (daemon) {
            return serveDaemon();
        }
        if (json) {
            throw new ParameterException(spec.commandLine(), "--json only applies to --daemon");
        }
        final var formatArguments = new FormatRun.Arguments(
                paths,
                check,
                threads.orElse(Runtime.getRuntime().availableProcessors()),
                cache,
                excludes,
                !noDefaultExcludes,
                classpath.filter(_ -> !noAnalyze),
                ratchetFrom,
                diagnostics);
        if (beforeEndOfOptions().contains(STDIN)) {
            if (paths.size() > 1) {
                throw new ParameterException(spec.commandLine(), "'-' reads stdin and cannot be combined with file arguments");
            }
            return runStdin(formatArguments);
        }
        if (paths.isEmpty()) {
            if (stdinIsATerminal) {
                throw new ParameterException(spec.commandLine(), "No input: pass files or directories, or pipe source on stdin.");
            }
            return runStdin(formatArguments);
        }
        return FormatRun.runPaths(formatArguments, err);
    }

    private int runStdin(final FormatRun.Arguments formatArguments) {
        if (ratchetFrom.isPresent()) {
            throw new ParameterException(spec.commandLine(), "--ratchet-from selects files and cannot be used with stdin");
        }
        return FormatRun.runStdin(formatArguments, ProjectFile.forDirectory(workingDirectory), in, out, err);
    }

    private int serveDaemon() {
        if (check || !paths.isEmpty()) {
            throw new ParameterException(spec.commandLine(), "--daemon reads requests from stdin and cannot be combined with --check or paths");
        }
        final var parseResult = spec.commandLine().getParseResult();
        final var unsupported = Stream.of("--cache", "--exclude", "--ratchet-from", "--diagnostics")
                .filter(parseResult::hasMatchedOption)
                .toList();
        if (!unsupported.isEmpty()) {
            throw new ParameterException(
                    spec.commandLine(),
                    "--daemon does not support " + String.join(", ", unsupported) + "; the daemon formats each request on its own");
        }
        if (parseResult.hasMatchedOption("--classpath")) {
            throw new ParameterException(spec.commandLine(), "--daemon takes the classpath per request, not as --classpath");
        }
        Daemon.serve(json ? Wire.JSON : Wire.BINARY, in, out, threads.orElse(Daemon.DEFAULT_THREADS));
        return ExitCode.OK;
    }

    private List<Path> beforeEndOfOptions() {
        final var expanded = spec.commandLine().getParseResult().expandedArgs();
        final var delimiter = expanded.indexOf(spec.parser().endOfOptionsDelimiter());
        final var afterDelimiter = delimiter < 0 ? 0 : expanded.size() - delimiter - 1;
        return paths.subList(0, paths.size() - afterDelimiter);
    }
}
