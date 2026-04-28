package io.github.jschneidereit.grind.spotless;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.Grind;
import io.github.jschneidereit.grind.Position;

public final class GrindFormatterStep {

    private GrindFormatterStep() {}

    public static String apply(final String source) {
        final var result = Grind.formatWithResult(source);
        if (result.hasErrors()) {
            final var error = result.diagnostics().stream()
                .filter(Diagnostic::isError)
                .findFirst()
                .orElseThrow();
            final var posStr = switch (error.position()) {
                case Position.At at -> at.line() + ":" + at.column();
                case Position.Unknown u -> "?:?";
            };
            throw new IllegalArgumentException("grind: " + posStr + ": " + error.message());
        }
        return result.output();
    }
}
