package io.javafmt.cli;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class Main {

    public void main(final String[] args) {
        final int exit;
        try {
            exit = Cli.run(args, System.in, System.out, System.err);
        } catch (RuntimeException e) {
            reportInternalError(e);
            System.exit(2);
            return;
        }
        if (exit != 0) {
            System.exit(exit);
        }
    }

    /**
     * Prints the stack trace alongside the bug-report message: "please file a bug" with no trace
     * leaves the reporter nothing to file, and the trace is what makes the report actionable.
     */
    private void reportInternalError(final RuntimeException e) {
        System.err.println("javafmt: internal error: " + e.getMessage() + " — please file a bug at https://github.com/javafmt-io/javafmt/issues");
        e.printStackTrace(System.err);
    }
}
