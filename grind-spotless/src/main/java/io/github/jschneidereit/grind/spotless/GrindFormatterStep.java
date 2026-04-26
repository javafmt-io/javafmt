package io.github.jschneidereit.grind.spotless;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.Grind;

public final class GrindFormatterStep {

    private GrindFormatterStep() {}

    public static String apply(final String source) {
        final var result = Grind.formatWithResult(source);
        if (result.hasErrors()) {
            final var error = result.diagnostics().stream()
                .filter(Diagnostic::isError)
                .findFirst()
                .orElseThrow();
            final var pos = error.position();
            throw new IllegalArgumentException(
                "grind: " + pos.line() + ":" + pos.column() + ": " + error.message());
        }
        return result.output();
    }
}
