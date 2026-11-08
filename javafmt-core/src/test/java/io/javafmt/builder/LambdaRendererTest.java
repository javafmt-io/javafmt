package io.javafmt.builder;

import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LambdaRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source)))
                .stripTrailing();
    }

    @Test
    void singleImplicitParam_noParens() {
        assertThat(format("class Foo { void test() { run(x -> x); } }"))
                .isEqualTo("""
                           class Foo {

                               void test() {run(x -> x);}
                           }""".stripIndent());
    }

    @Test
    void twoParams_parenthesized() {
        assertThat(format("class Foo { void test() { combine((a, b) -> a + b); } }"))
                .isEqualTo("""
                           class Foo {

                               void test() {combine((a, b) -> a + b);}
                           }""".stripIndent());
    }

    @Test
    void zeroParams() {
        assertThat(format("class Foo { void test() { run(() -> 42); } }"))
                .isEqualTo("""
                           class Foo {

                               void test() {run(() -> 42);}
                           }""".stripIndent());
    }

    @Test
    void blockBody() {
        assertThat(format("class Foo { void test() { run(x -> {\n doIt(x); }); } }"))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   run(x -> {
                                       doIt(x);
                                   });
                               }
                           }""".stripIndent());
    }
}
