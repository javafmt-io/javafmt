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
        final var result = new HideUtilityClassConstructor().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst()).isInstanceOf(Diagnostic.Warning.class);
        assertThat(result.diagnostics().getFirst().message()).containsIgnoringCase("private");
    }

    @Test
    void utilityClassWithPrivateConstructorProducesNoWarning() {
        final var unit = JavaParser.parseUnit("""
            class Utils {

                private Utils() {}
                public static int helper() { return 0; }
            }
            """);
        final var result = new HideUtilityClassConstructor().apply(LintContext.parseOnly(unit));
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
        final var result = new HideUtilityClassConstructor().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void abstractClassIsNotUtilityClass() {
        final var unit = JavaParser.parseUnit("""
            abstract class Base {

                public static int helper() { return 0; }
            }
            """);
        final var result = new HideUtilityClassConstructor().apply(LintContext.parseOnly(unit));
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
        final var result = new HideUtilityClassConstructor().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).hasSize(1);
    }

    /**
     * The three shapes javafmt warned about and Checkstyle does not. Each was found by running
     * javafmt over its own source and then asking {@code HideUtilityClassConstructorCheck} what it
     * says about the same shape, through the oracle this module already carries.
     */
    @Test
    void aClassWithASuperclassIsNotAUtilityClass() {
        // `LintRewriteException extends RuntimeException` carries a serialVersionUID and nothing
        // else static. A class that extends something is not a container of static helpers,
        // whatever its members look like, and Checkstyle skips it outright.
        final var unit = JavaParser.parseUnit("""
            class Fixture extends RuntimeException {

                private static final long serialVersionUID = 1L;

                Fixture(final String message) {
                    super(message);
                }
            }
            """);
        final var result = new HideUtilityClassConstructor().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void privateStaticMembersAreNotAStaticApi() {
        // `CommentScannerTest` holds one private static helper and a @Nested class. Nothing about
        // it is reachable without an instance, so there is no utility API to hide a constructor
        // from. Checkstyle reads the class's API, not its members.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                private static int helper() {
                    return 0;
                }
            }
            """);
        final var result = new HideUtilityClassConstructor().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void anyPrivateConstructorHidesTheClass() {
        // The rule asked for a private *no-arg* constructor. A private constructor taking arguments
        // hides the class just as well — nothing outside can call either one.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                public static int helper() {
                    return 0;
                }

                private Fixture(final int unused) {}
            }
            """);
        final var result = new HideUtilityClassConstructor().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void implementingAnInterfaceStillLeavesAUtilityClass() {
        // Only a superclass excludes it; an interface carries no state to inherit.
        final var unit = JavaParser.parseUnit("""
            class Fixture implements Runnable {

                public static int helper() {
                    return 0;
                }
            }
            """);
        final var result = new HideUtilityClassConstructor().apply(LintContext.parseOnly(unit));
        assertThat(result.diagnostics()).hasSize(1);
    }
}
