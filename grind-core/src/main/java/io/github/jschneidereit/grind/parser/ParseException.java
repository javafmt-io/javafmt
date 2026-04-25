package io.github.jschneidereit.grind.parser;

import io.github.jschneidereit.grind.Diagnostic;

import lombok.Getter;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

public final class ParseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final transient List<Diagnostic> diagnostics;

    ParseException(final String message, final List<Diagnostic> diagnostics) {
        super(message);
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
