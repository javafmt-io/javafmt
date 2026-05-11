package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class HideUtilityClassConstructorTest {

    @Test
    void utilityClassWithoutPrivateConstructorProducesWarning() {
        final var unit = JavaParser.parseUnit("""
            class Utils {
                public static int helper() { return 0; }
            }
            """);
        final var result = new HideUtilityClassConstructor().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().get(0)).isInstanceOf(Diagnostic.Warning.class);
        assertThat(result.diagnostics().get(0).message()).containsIgnoringCase("private");
    }

    @Test
    void utilityClassWithPrivateConstructorProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Utils {
                private Utils() {}
                public static int helper() { return 0; }
            }
            """);
        final var result = new HideUtilityClassConstructor().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void classWithInstanceMethodIsNotUtilityClass() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                public static int helper() { return 0; }
                public void instanceMethod() {}
            }
            """);
        final var result = new HideUtilityClassConstructor().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void abstractClassIsNotUtilityClass() {
        final var unit = JavaParser.parseUnit("""
            abstract class Base {
                public static int helper() { return 0; }
            }
            """);
        final var result = new HideUtilityClassConstructor().apply(unit);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void classWithOnlyStaticFieldsAndNoMethodsIsUtilityCandidate() {
        final var unit = JavaParser.parseUnit("""
            class Constants {
                public static final int MAX = 100;
                public static final int MIN = 0;
            }
            """);
        final var result = new HideUtilityClassConstructor().apply(unit);
        assertThat(result.diagnostics()).hasSize(1);
    }
}
