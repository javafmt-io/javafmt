package io.github.jschneidereit.grind.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.Grind;
import io.github.jschneidereit.grind.parser.JavaParser;

import org.junit.jupiter.api.Test;

class NeedBracesTest {

    @Test
    void emitsTwoEditsForSingleLineIfWithoutBraces() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    if (x > 0) doSomething();
                }
                native void doSomething();
            }
            """);
        final var result = new NeedBraces().apply(unit);
        assertThat(result.edits()).hasSize(2);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void emitsFourEditsWhenIfAndElseBothLackBraces() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    if (x > 0) doA();
                    else doB();
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new NeedBraces().apply(unit);
        assertThat(result.edits()).hasSize(4);
    }

    @Test
    void elseIfChainOnlyBracesUnbracedTerminalBodies() {
        // Outer if's body is unbraced (2 edits). The else-if's IfTree itself is NOT
        // wrapped — it's `else if`, valid Java grammar — but the inner if's then-body
        // is also unbraced (2 more edits). Total: 4 edits.
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    if (x > 0) doA();
                    else if (x < 0) doB();
                }
                native void doA();
                native void doB();
            }
            """);
        final var result = new NeedBraces().apply(unit);
        assertThat(result.edits()).hasSize(4);
    }

    @Test
    void elseIfWithBracedBodiesEmitsZeroEdits() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    if (x > 0) {
                        doA();
                    } else if (x < 0) {
                        doB();
                    } else {
                        doC();
                    }
                }
                native void doA();
                native void doB();
                native void doC();
            }
            """);
        final var result = new NeedBraces().apply(unit);
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void emitsTwoEditsForUnbracedWhile() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    while (x > 0) tick();
                }
                native void tick();
            }
            """);
        final var result = new NeedBraces().apply(unit);
        assertThat(result.edits()).hasSize(2);
    }

    @Test
    void emitsTwoEditsForUnbracedDoWhile() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test(final int x) {
                    do tick(); while (x > 0);
                }
                native void tick();
            }
            """);
        final var result = new NeedBraces().apply(unit);
        assertThat(result.edits()).hasSize(2);
    }

    @Test
    void emitsTwoEditsForUnbracedForLoop() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {
                void test() {
                    for (int i = 0; i < 10; i++) tick();
                }
                native void tick();
            }
            """);
        final var result = new NeedBraces().apply(unit);
        assertThat(result.edits()).hasSize(2);
    }

    @Test
    void emitsTwoEditsForUnbracedEnhancedForLoop() {
        final var unit = JavaParser.parseUnit("""
            import java.util.List;
            class Fixture {
                void test(final List<String> items) {
                    for (String s : items) tick(s);
                }
                native void tick(String s);
            }
            """);
        final var result = new NeedBraces().apply(unit);
        assertThat(result.edits()).hasSize(2);
    }

    @Test
    void emitsZeroEditsWhenAllBodiesAreAlreadyBlocks() {
        final var unit = JavaParser.parseUnit("""
            import java.util.List;
            class Fixture {
                void test(final int x, final List<String> items) {
                    if (x > 0) {
                        doA();
                    } else {
                        doB();
                    }
                    while (x > 0) {
                        tick();
                    }
                    do {
                        tick();
                    } while (x > 0);
                    for (int i = 0; i < 10; i++) {
                        tick();
                    }
                    for (String s : items) {
                        tick();
                    }
                }
                native void doA();
                native void doB();
                native void tick();
            }
            """);
        final var result = new NeedBraces().apply(unit);
        assertThat(result.edits()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void formatInsertsBracesAroundUnbracedSingleLineIf() {
        final var src = """
            class Fixture {
                void test(final int x) {
                    if (x > 0) doSomething();
                }
                native void doSomething();
            }
            """;
        final var output = Grind.format(src);
        assertThat(output).contains("if (x > 0) {");
        assertThat(output).contains("doSomething();");
        assertThat(output).doesNotContain("if (x > 0) doSomething();");
    }

    @Test
    void formatInsertsBracesAroundMultiLineUnbracedIf() {
        final var src = """
            class Fixture {
                void test(final int x) {
                    if (x > 0)
                        doSomething();
                }
                native void doSomething();
            }
            """;
        final var output = Grind.format(src);
        assertThat(output).contains("if (x > 0) {");
        assertThat(output).contains("doSomething();");
    }

    @Test
    void formatBracesLongBodyAndPrinterBreaksAcrossLines() {
        // The body, once braced, exceeds nothing the printer would ever try to inline:
        // the rule's job is just to insert `{ ... }`. The printer renders blocks across lines.
        final var src = """
            class Fixture {
                void test(final int x) {
                    if (x > 0) doSomethingWithAVeryLongMethodName(x, x, x, x, x, x, x, x, x, x, x, x, x, x);
                }
                native void doSomethingWithAVeryLongMethodName(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j, int k, int l, int m, int n);
            }
            """;
        final var output = Grind.format(src);
        assertThat(output).contains("if (x > 0) {");
        assertThat(output).contains("}");
    }

    @Test
    void formatIsIdempotent() {
        final var src = """
            class Fixture {
                void test(final int x) {
                    if (x > 0) doA();
                    else doB();
                    while (x > 0) tick();
                    for (int i = 0; i < 10; i++) tick();
                }
                native void doA();
                native void doB();
                native void tick();
            }
            """;
        final var first = Grind.format(src);
        final var second = Grind.format(first);
        assertThat(second).isEqualTo(first);
    }
}
