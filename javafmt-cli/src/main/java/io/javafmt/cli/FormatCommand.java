package io.javafmt.cli;

import io.javafmt.JavafmtVersion;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command( name = "javafmt", mixinStandardHelpOptions = true, versionProvider = FormatCommand.Version.class, showAtFileInUsageHelp = true, showEndOfOptionsDelimiterInUsageHelp = true, description = { "Formats Java source in place. With no paths, or with '-', reads from stdin and writes the result to stdout.", "Directories are searched recursively for .java files.", }, exitCodeListHeading = "%nExit Codes:%n", exitCodeList = { "0:nothing to do", "1:a file would be reformatted, or could not be formatted", "2:bad usage, or a file could not be read or written", })
final class FormatCommand implements Callable<Integer> {

    private static final Path STDIN = Path.of("-");

    private final InputStream in;

    private final PrintStream out;

    private final PrintStream err;

    private final boolean stdinIsATerminal;

    @Spec
    private CommandSpec spec;

    @Option(names = "--check", description = "Do not write anything; exit 1 if any file would be reformatted.")
    private boolean check;

    @Option( names = "--diagnostics", paramLabel = "<format>", defaultValue = "text", description = "Diagnostic format on stderr: text, or json for NDJSON. Default: ${DEFAULT-VALUE}.")
    private DiagnosticsFormat diagnostics = DiagnosticsFormat.TEXT;

    @Option(names = "--exclude", paramLabel = "<glob>", description = "Skip paths matching the glob. Repeatable.")
    private List<String> excludes = new ArrayList<>();

    @Option(names = "--no-default-excludes", description = "Search build, target, out, generated, node_modules and .git too.")
    private boolean noDefaultExcludes;

    private int threads = Runtime.getRuntime().availableProcessors();

    @Option(names = "--cache", paramLabel = "<file>", description = "Skip files unchanged since the last run recorded in this file.")
    private Optional<Path> cache = Optional.empty();

    @ArgGroup(exclusive = false)
    @Nullable
    private Analysis analysis;

    @Parameters(paramLabel = "<path>", description = "Files or directories to format, or '-' for stdin.")
    private List<Path> paths = new ArrayList<>();

    FormatCommand(final InputStream in, final PrintStream out, final PrintStream err, final boolean stdinIsATerminal) {
        this.in = in;
        this.out = out;
        this.err = err;
        this.stdinIsATerminal = stdinIsATerminal;
    }

    @Option(names = "--threads", paramLabel = "<n>", description = "Files to format concurrently. Default: the CPU count.")
    void setThreads(final int threads) {
        if (threads <= 0) {
            throw new ParameterException(spec.commandLine(), "--threads must be a positive integer, but was: " + threads);
        }
        this.threads = threads;
    }

    @Override
    public Integer call() {
        final var args = new FormatRun.Args(
                paths,
                check,
                threads,
                cache,
                excludes,
                !noDefaultExcludes,
                Optional.ofNullable(analysis).filter(a -> a.analyze).map(a -> a.classpath),
                diagnostics == DiagnosticsFormat.JSON);
        if (beforeEndOfOptions(paths).contains(STDIN)) {
            if (paths.size() > 1) {
                throw new ParameterException(spec.commandLine(), "'-' reads stdin and cannot be combined with file arguments");
            }
            return FormatRun.runStdin(args, in, out, err);
        }
        if (paths.isEmpty()) {
            if (stdinIsATerminal) {
                throw new ParameterException(spec.commandLine(), "No input: pass files or directories, or pipe source on stdin.");
            }
            return FormatRun.runStdin(args, in, out, err);
        }
        return FormatRun.runPaths(args, err);
    }

    private List<Path> beforeEndOfOptions(final List<Path> positionals) {
        final var expanded = spec.commandLine().getParseResult().expandedArgs();
        final var delimiter = expanded.indexOf(spec.parser().endOfOptionsDelimiter());
        final var afterDelimiter = delimiter < 0 ? 0 : expanded.size() - delimiter - 1;
        return positionals.subList(0, positionals.size() - afterDelimiter);
    }

    enum DiagnosticsFormat { JSON, TEXT, }

    static final class Analysis {

        @Option(names = "--analyze", required = true, description = "Enable lint rules that need resolved types. Requires --classpath.")
        private boolean analyze;

        @Option( names = "--classpath", required = true, paramLabel = "<classpath>", description = "Classpath used by --analyze. Pass @<file> to read it from a file.")
        private String classpath = "";
    }

    static final class Version implements IVersionProvider {

        @Override
        public String[] getVersion() {
            return new String[] {"javafmt " + JavafmtVersion.VERSION,};
        }
    }
}
