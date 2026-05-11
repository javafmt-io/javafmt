package io.javafmt.spotless;

import com.diffplug.spotless.FormatterStep;
import io.javafmt.Diagnostic;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.Position;

public final class JavafmtFormatterStep {

    private static final String NAME = "javafmt";

    private JavafmtFormatterStep() {}

    public static FormatterStep create() {
        return create(JavafmtConfig.defaults());
    }

    public static FormatterStep create(final JavafmtConfig config) {
        return FormatterStep.create(NAME, config, cfg -> input -> apply(input, cfg));
    }

    public static String apply(final String source) {
        return apply(source, JavafmtConfig.defaults());
    }

    public static String apply(final String source, final JavafmtConfig config) {
        final var result = Javafmt.formatWithResult(source, config);
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
