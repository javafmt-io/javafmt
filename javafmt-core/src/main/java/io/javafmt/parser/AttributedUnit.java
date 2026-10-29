package io.javafmt.parser;

import java.util.Objects;

/**
 * A parsed compilation unit paired with javac's {@link Attribution} services. Produced by
 * {@link JavaParser#parseAndAttribute(String, String)} when a caller wants type-aware lint
 * rules to run.
 */
public record AttributedUnit(ParsedUnit unit, Attribution attribution) {

    public AttributedUnit {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(attribution, "attribution");
    }
}
