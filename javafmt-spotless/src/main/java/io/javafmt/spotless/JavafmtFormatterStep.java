package io.javafmt.spotless;

import io.javafmt.Diagnostic;
import io.javafmt.Javafmt;
import io.javafmt.Position;

public final class JavafmtFormatterStep {

    private JavafmtFormatterStep() {}

    public static String apply(final String source) {
        final var result = Javafmt.formatWithResult(source);
        if (result.hasErrors()) {
            final var error = result.diagnostics().stream()
                .filter(Diagnostic::isError)
                .findFirst()
                .orElseThrow();
            final var posStr = switch (error.position()) {
                case Position.At at -> at.line() + ":" + at.column();
                case Position.Unknown u -> "?:?";
            };
            throw new IllegalArgumentException("javafmt: " + posStr + ": " + error.message());
        }
        return result.output();
    }
}
