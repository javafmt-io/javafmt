package io.javafmt.lint;

import io.javafmt.parser.AttributedUnit;
import io.javafmt.parser.Attribution;
import io.javafmt.parser.ParsedUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-rule input bundle. Always carries the {@link ParsedUnit}; carries an
 * {@link Attribution} only when the run opted into javac attribution
 * ({@code --analyze}). Rules check {@link #attribution()} and upgrade their
 * behavior when type information is available.
 */
public record LintContext(ParsedUnit unit, Optional<Attribution> attribution) {

    public LintContext {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(attribution, "attribution");
    }

    public static LintContext parseOnly(final ParsedUnit unit) {
        return new LintContext(Objects.requireNonNull(unit, "unit"), Optional.empty());
    }

    public static LintContext attributed(final AttributedUnit attributed) {
        Objects.requireNonNull(attributed, "attributed");
        return new LintContext(attributed.unit(), Optional.of(attributed.attribution()));
    }
}
