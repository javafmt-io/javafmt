package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.printer.Printer;

import org.junit.jupiter.api.Test;

class MethodChainRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return Printer.print(DocBuilder.build(JavaParser.parse(source)), WIDTH);
    }

    @Test
    void shortChain_fitsOnOneLine() {
        assertThat(format("class Foo { void test() { result.stream().toList(); } }")).isEqualTo("""
            class Foo {
                void test() {
                    result.stream().toList();
                }
            }""".stripIndent());
    }

    @Test
    void standaloneCall_noChain_rendersFlat() {
        assertThat(format("class Foo { void test() { doSomething(42); } }")).isEqualTo("""
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
            + ".filter(element -> element.isActive())"
            + ".map(element -> element.getFullName())"
            + ".sorted(Comparator.naturalOrder())"
            + ".limit(100)"
            + ".collect(Collectors.toList()); } }";
        assertThat(format(input)).isEqualTo("""
            class Foo {
                void test() {
                    result.stream()
                            .filter((element)->element.isActive())
                            .map((element)->element.getFullName())
                            .sorted(Comparator.naturalOrder())
                            .limit(100)
                            .collect(Collectors.toList());
                }
            }""".stripIndent());
    }

    @Test
    void returnWithChain_breaksCorrectly() {
        final var input = "class Foo { List<String> test() { return "
            + "result.stream()"
            + ".filter(element -> element.isActive())"
            + ".map(element -> element.getFullName())"
            + ".sorted(Comparator.naturalOrder())"
            + ".limit(100)"
            + ".collect(Collectors.toList()); } }";
        assertThat(format(input)).isEqualTo("""
            class Foo {
                List<String> test() {
                    return result.stream()
                            .filter((element)->element.isActive())
                            .map((element)->element.getFullName())
                            .sorted(Comparator.naturalOrder())
                            .limit(100)
                            .collect(Collectors.toList());
                }
            }""".stripIndent());
    }
}
