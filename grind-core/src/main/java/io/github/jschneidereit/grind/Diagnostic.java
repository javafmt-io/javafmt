package io.github.jschneidereit.grind;

public sealed interface Diagnostic permits Diagnostic.ParseError, Diagnostic.Warning {

    String message();

    Position position();

    default boolean isError() {
        return this instanceof ParseError;
    }

    record ParseError(String message, Position position) implements Diagnostic {}

    record Warning(String message, Position position) implements Diagnostic {}
}
