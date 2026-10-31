package io.javafmt.parser;

import java.util.Objects;

public record AttributionError(long offset, String message) {
    public AttributionError {
        Objects.requireNonNull(message, "message");
    }
}
