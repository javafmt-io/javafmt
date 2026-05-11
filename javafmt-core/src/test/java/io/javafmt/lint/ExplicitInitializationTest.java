package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExplicitInitializationTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "class Fixture { int x = 0; }",
        "class Fixture { byte x = 0; }",
        "class Fixture { short x = 0; }",
        "class Fixture { boolean x = false; }",
        "class Fixture { Object x = null; }",
        "class Fixture { String x = null; }",
    })
    void redundantDefaultInitializerRemovedFromField(final String src) {
        final var unit = JavaParser.parseUnit(src);
        final var result = new ExplicitInitialization().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void longFieldWithZeroLRemovedFromField() {
        final var unit = JavaParser.parseUnit("class Fixture { long x = 0L; }");
        final var result = new ExplicitInitialization().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEmpty();
    }

    @Test
    void doubleFieldWithZeroDotZeroRemovedFromField() {
        final var unit = JavaParser.parseUnit("class Fixture { double x = 0.0; }");
        final var result = new ExplicitInitialization().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEmpty();
    }

    @Test
    void floatFieldWithZeroFRemovedFromField() {
        final var unit = JavaParser.parseUnit("class Fixture { float x = 0.0f; }");
        final var result = new ExplicitInitialization().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEmpty();
    }

    @Test
    void nonDefaultValueFieldIsPreserved() {
        final var unit = JavaParser.parseUnit("class Fixture { int x = 1; }");
        final var result = new ExplicitInitialization().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void nonNullReferenceInitializerIsPreserved() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                String name = "hello";
            }
            """);
        final var result = new ExplicitInitialization().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void localVariableDefaultInitNotRemoved() {
        // Rule only applies to fields, not locals
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    int x = 0;
                }
            }
            """);
        final var result = new ExplicitInitialization().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void staticFieldWithDefaultInitIsRemoved() {
        final var unit = JavaParser.parseUnit("class Fixture { static int count = 0; }");
        final var result = new ExplicitInitialization().apply(unit);
        assertThat(result.edits()).hasSize(1);
    }

    @Test
    void arrayFieldWithNullInitIsRemoved() {
        final var unit = JavaParser.parseUnit("class Fixture { int[] data = null; }");
        final var result = new ExplicitInitialization().apply(unit);
        assertThat(result.edits()).hasSize(1);
    }
}
