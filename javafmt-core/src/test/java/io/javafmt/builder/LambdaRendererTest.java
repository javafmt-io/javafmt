package io.javafmt.builder;

import org.junit.jupiter.api.Test;

import static io.javafmt.builder.RendererTestSupport.format;
import static org.assertj.core.api.Assertions.assertThat;

class LambdaRendererTest {

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
