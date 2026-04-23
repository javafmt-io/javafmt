package io.github.jschneidereit.grind;

public record Diagnostic(Kind kind, String message) {

    public enum Kind { PARSE_ERROR, WARNING }

    public static Diagnostic parseError(final String message) {
        return new Diagnostic(Kind.PARSE_ERROR, message);
    }

    public static Diagnostic warning(final String message) {
        return new Diagnostic(Kind.WARNING, message);
    }
}
