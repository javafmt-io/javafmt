package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.printer.Printer;

import org.junit.jupiter.api.Test;

class LambdaRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source)));
    }

    @Test
    void singleImplicitParam_noParens() {
        assertThat(format("class Foo { void test() { run(x -> x); } }")).isEqualTo("""
            class Foo {
                void test() {
                    run(x -> x);
                }
            }""".stripIndent());
    }

    @Test
    void twoParams_parenthesized() {
        assertThat(format("class Foo { void test() { combine((a, b) -> a + b); } }")).isEqualTo("""
            class Foo {
                void test() {
                    combine((a, b) -> a + b);
                }
            }""".stripIndent());
    }

    @Test
    void zeroParams() {
        assertThat(format("class Foo { void test() { run(() -> 42); } }")).isEqualTo("""
            class Foo {
                void test() {
                    run(() -> 42);
                }
            }""".stripIndent());
    }

    @Test
    void blockBody() {
        assertThat(format("class Foo { void test() { run(x -> { doIt(x); }); } }")).isEqualTo("""
            class Foo {
                void test() {
                    run(
                        x -> {
                            doIt(x);
                        }
                    );
                }
            }""".stripIndent());
    }
}
