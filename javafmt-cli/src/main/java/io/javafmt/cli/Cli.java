package io.javafmt.cli;

import java.io.Console;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import picocli.CommandLine;

@UtilityClass
public final class Cli {

    private final int INTERNAL_ERROR = 2;

    public int run(final String[] args, final InputStream in, final PrintStream out, final PrintStream err) {
        return run(args, in, out, err, stdinIsATerminal());
    }

    /**
     * @param stdinIsATerminal whether a person is typing at the other end, in which case falling
     *     back to stdin would hang with no prompt; the tests pass it explicitly because a test
     *     harness never has a terminal.
     */
    int run(final String[] args, final InputStream in, final PrintStream out, final PrintStream err, final boolean stdinIsATerminal) {
        final var outWriter = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
        final var errWriter = new PrintWriter(new OutputStreamWriter(err, StandardCharsets.UTF_8), true);
        try {
            return new CommandLine(new FormatCommand(in, out, err, stdinIsATerminal)).setCaseInsensitiveEnumValuesAllowed(true)
                    .setOut(outWriter)
                    .setErr(errWriter)
                    .setExecutionExceptionHandler(Cli::reportInternalError)
                    .execute(args);
        } finally {
            outWriter.flush();
            errWriter.flush();
        }
    }

    private boolean stdinIsATerminal() {
        return Optional.ofNullable(System.console()).map(Console::isTerminal).orElse(false);
    }

    /**
     * Prints the stack trace alongside the bug-report message: "please file a bug" with no trace
     * leaves the reporter nothing to file, and the trace is what makes the report actionable.
     */
    private int reportInternalError(final Exception e, final CommandLine commandLine, final CommandLine.ParseResult parseResult) {
        final var err = commandLine.getErr();
        err.println("javafmt: internal error: " + e.getMessage() + " — please file a bug at https://github.com/javafmt-io/javafmt/issues");
        e.printStackTrace(err);
        return INTERNAL_ERROR;
    }
}
