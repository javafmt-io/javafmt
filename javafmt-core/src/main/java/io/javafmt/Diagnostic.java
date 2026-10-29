package io.javafmt;

import java.util.Objects;

/**
 * One issue raised while formatting a source: a parse failure, a lint rule that could not
 * rewrite the source, an internal javafmt error, or a non-fatal warning. Attached to a
 * {@link FormatResult} rather than thrown, so a caller formatting many files can collect every
 * diagnostic without a failure in one file aborting the rest.
 */
public sealed interface Diagnostic permits Diagnostic.ParseError, Diagnostic.LintError, Diagnostic.InternalError, Diagnostic.Warning {

    /** A human-readable description of the issue. */
    String message();

    /** Where in the source the issue occurred, or {@link Position#UNKNOWN} if not applicable. */
    Position position();

    /** Whether this diagnostic represents a failure rather than an advisory {@link Warning}. */
    default boolean isError() {
        return switch (this) {
            case ParseError ignored -> true;
            case LintError ignored -> true;
            case InternalError ignored -> true;
            case Warning ignored -> false;
        };
    }

    /** {@code file:line:column: error|warning: message}, or without the position when unknown. */
    default String describe(final String file) {
        return file + (position().display().isEmpty() ? "" : ":" + position().display()) + ": " + (isError() ? "error" : "warning") + ": "
                + message();
    }

    /**
     * {@code line:column: message}, omitting both the file name and the {@code error}/
     * {@code warning} severity label that {@link #describe(String)} includes — for callers that
     * already show the file name and want to supply their own severity prefix.
     */
    default String describeBare() {
        return (position().display().isEmpty() ? "" : position().display() + ": ") + message();
    }

    private static void requireParts(final String message, final Position position) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(position, "position");
    }

    /** The source could not be parsed. The input is at fault. */
    record ParseError(String message, Position position) implements Diagnostic {

        public ParseError {
            requireParts(message, position);
        }
    }

    /** A lint rule rejected the source and could not rewrite it. */
    record LintError(String message, Position position) implements Diagnostic {

        public LintError {
            requireParts(message, position);
        }
    }

    /**
     * javafmt itself failed on source that parsed cleanly — a renderer gap, a lint rule
     * crash, or an output-verification mismatch. The source is returned unchanged. This is
     * always a javafmt bug, and is kept distinct from {@link ParseError} so that a coverage
     * gap in the formatter is never reported to users as a problem with their code.
     *
     * <p>Shares a simple name with {@link java.lang.InternalError}, which this interface never
     * references; every use site spells it {@code Diagnostic.InternalError}.
     */
    @SuppressWarnings("JavaLangClash")
    record InternalError(String message, Position position) implements Diagnostic {

        public InternalError {
            requireParts(message, position);
        }
    }

    /** A non-fatal advisory: formatting succeeded, but something the caller asked for did not happen. */
    record Warning(String message, Position position) implements Diagnostic {

        public Warning {
            requireParts(message, position);
        }
    }
}
