package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModifierOrderTest {

    @Test
    void staticPublicReorderedToPublicStatic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                static public int x = 0;
            }
            """);
        final var result = new ModifierOrder().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("public static");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void finalStaticReorderedToStaticFinal() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                final static int X = 1;
            }
            """);
        final var result = new ModifierOrder().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("static final");
    }

    @Test
    void abstractPublicMethodReorderedToPublicAbstract() {
        final var unit = JavaParser.parseUnit("""
            abstract class Fixture {
                abstract public void method();
            }
            """);
        final var result = new ModifierOrder().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("public abstract");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "class Fixture { public static int x = 0; }",
        "class Fixture { public final int x = 0; }",
        "class Fixture { private static int x = 0; }",
        "abstract class Fixture { public abstract void method(); }",
    })
    void alreadyCanonicalOrderProducesNoEdits(final String src) {
        final var unit = JavaParser.parseUnit(src);
        final var result = new ModifierOrder().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void singleModifierProducesNoEdits() {
        final var unit = JavaParser.parseUnit("class Fixture { public int x = 0; }");
        final var result = new ModifierOrder().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void nativeSynchronizedReorderedToSynchronizedNative() {
        // JLS canonical: synchronized (index 9) comes before native (index 10)
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                native synchronized void method();
            }
            """);
        final var result = new ModifierOrder().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("synchronized native");
    }
}
