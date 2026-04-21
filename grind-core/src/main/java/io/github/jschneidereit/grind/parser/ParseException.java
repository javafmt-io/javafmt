package io.github.jschneidereit.grind.parser;

import lombok.Getter;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("serial")
public final class ParseException extends RuntimeException {

    @Getter
    private final List<Diagnostic<? extends JavaFileObject>> diagnostics;

    ParseException(final String message, final List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        super(message);
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    ParseException(final String message, final Throwable cause) {
        super(message, cause);
        this.diagnostics = List.of();
    }

}
