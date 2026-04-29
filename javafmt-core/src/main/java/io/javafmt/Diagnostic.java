package io.javafmt;

public sealed interface Diagnostic permits Diagnostic.ParseError, Diagnostic.LintError, Diagnostic.Warning {

    String message();

    Position position();

    default boolean isError() {
        return this instanceof ParseError || this instanceof LintError;
    }

    record ParseError(String message, Position position) implements Diagnostic {}

    record LintError(String message, Position position) implements Diagnostic {}

    record Warning(String message, Position position) implements Diagnostic {}
}
