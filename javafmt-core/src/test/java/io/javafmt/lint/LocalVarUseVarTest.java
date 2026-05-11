package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;

import org.junit.jupiter.api.Test;

class LocalVarUseVarTest {

    @Test
    void explicitTypeReplacedWithVarForNewExpression() {
        final var unit = JavaParser.parseUnit("""
            import java.util.ArrayList;
            class Fixture {
                void test() {
                    ArrayList<String> list = new ArrayList<String>();
                }
            }
            """);
        final var result = new LocalVarUseVar().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("var");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void widenedTypeNotReplaced() {
        // List<String> x = new ArrayList<String>() — declared type is wider, keep it
        final var unit = JavaParser.parseUnit("""
            import java.util.List;
            import java.util.ArrayList;
            class Fixture {
                void test() {
                    List<String> list = new ArrayList<String>();
                }
            }
            """);
        final var result = new LocalVarUseVar().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void diamondInferenceNotReplaced() {
        // ArrayList<String> x = new ArrayList<>() — diamond inference changes semantics
        final var unit = JavaParser.parseUnit("""
            import java.util.ArrayList;
            class Fixture {
                void test() {
                    ArrayList<String> list = new ArrayList<>();
                }
            }
            """);
        final var result = new LocalVarUseVar().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void primitiveTypeNotReplaced() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    int x = 0;
                }
            }
            """);
        final var result = new LocalVarUseVar().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void alreadyVarTypeProducesNoEdits() {
        final var unit = JavaParser.parseUnit("""
            import java.util.ArrayList;
            class Fixture {
                void test() {
                    var list = new ArrayList<String>();
                }
            }
            """);
        final var result = new LocalVarUseVar().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void noInitializerProducesNoEdits() {
        final var unit = JavaParser.parseUnit("""
            import java.util.ArrayList;
            class Fixture {
                void test(final ArrayList<String> list) {
                    ArrayList<String> copy;
                }
            }
            """);
        final var result = new LocalVarUseVar().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void fieldNotReplaced() {
        // Only locals, not fields
        final var unit = JavaParser.parseUnit("""
            import java.util.ArrayList;
            class Fixture {
                ArrayList<String> field = new ArrayList<String>();
            }
            """);
        final var result = new LocalVarUseVar().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void simpleTypeNoGenericsReplaced() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    StringBuilder sb = new StringBuilder();
                }
            }
            """);
        final var result = new LocalVarUseVar().apply(unit);
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().get(0).replacement()).isEqualTo("var");
    }
}
