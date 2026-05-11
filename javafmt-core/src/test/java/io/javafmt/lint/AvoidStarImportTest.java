package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class AvoidStarImportTest {

    @Test
    void nonStaticStarImportProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            import java.util.*;
            class Fixture {}
            """);
        final var result = new AvoidStarImport().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0)).isInstanceOf(Diagnostic.Warning.class);
    }

    @Test
    void staticStarImportProducesNoWarning() {
        // Only non-static star imports are warned — static star imports are handled separately
        final var unit = JavaParser.parseUnit("""
            import static java.util.Collections.*;
            class Fixture {}
            """);
        final var result = new AvoidStarImport().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void nonStarImportProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            import java.util.List;
            class Fixture {}
            """);
        final var result = new AvoidStarImport().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void multipleStarImportsProduceMultipleWarnings() {
        final var unit = JavaParser.parseUnit("""
            import java.util.*;
            import java.io.*;
            class Fixture {}
            """);
        final var result = new AvoidStarImport().apply(unit);
        assertThat(result.diagnostics()).hasSize(2);
    }
}
