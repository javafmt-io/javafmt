package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;
import org.junit.jupiter.api.Test;

class MethodChainRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source))).stripTrailing();
    }

    @Test
    void shortChain_fitsOnOneLine() {
        assertThat(format("class Foo { void test() { result.stream().toList(); } }"))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   result.stream().toList();
                               }
                           }""".stripIndent());
    }

    @Test
    void standaloneCall_noChain_rendersFlat() {
        assertThat(format("class Foo { void test() { doSomething(42); } }"))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   doSomething(42);
                               }
                           }""".stripIndent());
    }

    @Test
    void longChain_exceedsWidth_breaksBeforeDot() {
        final var input = "class Foo { void test() { "
            + "result.stream()"
            + ".filter(element->element.isActive())"
            + ".map(element->element.getFullName())"
            + ".sorted(Comparator.naturalOrder())"
            + ".limit(100)"
            + ".collect(Collectors.toList()); } }";
        assertThat(format(input))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   result.stream()
                                           .filter(element -> element.isActive())
                                           .map(element -> element.getFullName())
                                           .sorted(Comparator.naturalOrder())
                                           .limit(100)
                                           .collect(Collectors.toList());
                               }
                           }""".stripIndent());
    }

    @Test
    void argList_fitsFlat_staysOnOneLine() {
        assertThat(format("class Foo { void test() { someMethod(alpha, beta, gamma); } }"))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   someMethod(alpha, beta, gamma);
                               }
                           }""".stripIndent());
    }

    @Test
    void argList_doesNotFit_breaksAllArgs() {
        final var input = "class Foo { void test() { "
            + "someMethod(arg1111111111, arg2222222222, arg3333333333, arg4444444444, arg5555555555, "
            + "arg6666666666, arg7777777777, arg8888888888, arg9999999999); } }";
        assertThat(format(input))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   someMethod(
                                           arg1111111111,
                                           arg2222222222,
                                           arg3333333333,
                                           arg4444444444,
                                           arg5555555555,
                                           arg6666666666,
                                           arg7777777777,
                                           arg8888888888,
                                           arg9999999999);
                               }
                           }""".stripIndent());
    }

    @Test
    void chainWithOneLongCall_onlyThatCallBreaks() {
        final var input = "class Foo { void test() { "
            + "result.stream().filter(x -> x > 0).process("
            + "argumentOne12345, argumentTwo12345, argumentThree12345, argumentFour12345, "
            + "argumentFive12345, argumentSix12345, argumentSeven12345); } }";
        assertThat(format(input))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   result.stream()
                                           .filter(x -> x > 0)
                                           .process(
                                                   argumentOne12345,
                                                   argumentTwo12345,
                                                   argumentThree12345,
                                                   argumentFour12345,
                                                   argumentFive12345,
                                                   argumentSix12345,
                                                   argumentSeven12345);
                               }
                           }""".stripIndent());
    }

    @Test
    void blockBodyLambdaArg_staysInlineWithOpenParen() {
        final var input = "class Foo { void test() { runner.forEach(x -> { doIt(x); }); } }";
        assertThat(format(input))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   runner.forEach(x -> {
                                       doIt(x);
                                   });
                               }
                           }""".stripIndent());
    }

    @Test
    void invocationWithExplicitTypeArguments_rendersTypeArgsThroughDocIr() {
        // Explicit type arguments must travel through recursor.scan, not Tree.toString — the
        // latter bypasses the Doc IR and would crash Doc.Text on any \n a future pretty-printer
        // emits. A nested generic in the type args exercises the recursive scan path.
        final var input = "class Foo { void test() { Stream.<java.util.Map<String, Integer>>of(); } }";
        assertThat(format(input))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   Stream.<java.util.Map<String, Integer>>of();
                               }
                           }""".stripIndent());
    }

    @Test
    void returnWithChain_breaksCorrectly() {
        final var input = "class Foo { List<String> test() { return "
            + "result.stream()"
            + ".filter(element-> element.isActive())"
            + ".map(element ->element.getFullName())"
            + ".sorted(Comparator.naturalOrder())"
            + ".limit(100)"
            + ".collect(Collectors.toList()); } }";
        assertThat(format(input))
                .isEqualTo("""
                           class Foo {

                               List<String> test() {
                                   return result.stream()
                                           .filter(element -> element.isActive())
                                           .map(element -> element.getFullName())
                                           .sorted(Comparator.naturalOrder())
                                           .limit(100)
                                           .collect(Collectors.toList());
                               }
                           }""".stripIndent());
    }

    @Test
    void authorBrokeChain_isPreservedEvenWhenItWouldFit() {
        // Author placed each `.` on its own line; output keeps the same shape even though
        // the chain would fit on a single line.
        final var input = """
            class Foo {

                void test() {
                    result.stream()
                        .filter(x -> x.ok())
                        .toList();
                }
            }""".stripIndent();
        assertThat(format(input))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   result.stream()
                                           .filter(x -> x.ok())
                                           .toList();
                               }
                           }""".stripIndent());
    }

    @Test
    void singleLineChain_staysOnOneLine() {
        // Chain was inline in source; the group renders flat.
        assertThat(format("class Foo { void test() { result.stream().filter(x -> x.ok()).toList(); } }"))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   result.stream().filter(x -> x.ok()).toList();
                               }
                           }""".stripIndent());
    }

    @Test
    void twoLinkChainOverEighty_staysFlat() {
        // Two-link chains break down to "receiver.method(args)" — putting the call suffix on
        // a new line at +8 indent doesn't separate any structure, it just adds vertical
        // noise. So the chain-break rule requires >= 3 links; two-link chains rely on the
        // surrounding 150-char line width and the inner arg-list break.
        final var input = "class Foo { void test() { "
            + "stmts.stream().<Doc>map(s -> new Doc.Indent(new Doc.Concat(List.of(Doc.HARD_LINE, s)))); } }";
        assertThat(format(input))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   stmts.stream().<Doc>map(s -> new Doc.Indent(new Doc.Concat(List.of(Doc.HARD_LINE, s))));
                               }
                           }""".stripIndent());
    }

    @Test
    void chainOverEightyChars_breaksEvenWhenLineFits() {
        // Palantir-style: break method chains that would exceed 80 chars on a single line
        // even when the surrounding line width (150) would accommodate them. The chain here
        // is under 150 chars total but over 80 — it breaks.
        final var input = "class Foo { void test() { var foo = SomeServiceBuilder.builder()"
            + ".withThing1(thing1).withThing2(thing2).withThing3(thing3).build(); } }";
        assertThat(format(input))
                .isEqualTo("""
                           class Foo {

                               void test() {
                                   var foo = SomeServiceBuilder.builder()
                                           .withThing1(thing1)
                                           .withThing2(thing2)
                                           .withThing3(thing3)
                                           .build();
                               }
                           }""".stripIndent());
    }
}
