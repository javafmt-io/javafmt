package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Javafmt;
import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;
import org.junit.jupiter.api.Test;

/**
 * Multi-line literals (text blocks, traditional strings with embedded newlines) are
 * rendered verbatim — interior lines keep their authored leading whitespace, regardless
 * of the surrounding indent context. This is required for both semantic correctness
 * (changing interior whitespace changes the string's value under text-block incidental
 * whitespace stripping) and convergence (a renderer that prepended the surrounding
 * indent would re-indent the literal on every format pass, compounding indefinitely).
 */
class TextBlockRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source))).stripTrailing();
    }

    @Test
    void textBlockInsideMethod_interiorLinesKeepAuthoredIndent() {
        final var src = """
            class Foo {

                String t() {
                    return \"""
                        hello
                        \""";
                }
            }
            """;
        // Interior lines of the text block keep the 12-space indent they had in src,
        // even though the enclosing 'return' lands at column 8 in the output.
        assertThat(format(src)).isEqualTo("class Foo {\n\n    String t() {\n        return \"\"\"\n            hello\n            \"\"\";\n    }\n}");
    }

    @Test
    void textBlockInsideMethod_isIdempotentUnderFullFormat() {
        // Regression: before the Doc.Verbatim fix, the renderer prepended the enclosing
        // indent (8 spaces) to every interior line of the text block on every pass,
        // compounding by 8 each iteration and leaving Spotless unable to converge.
        final var src = """
            class Foo {

                String t() {
                    return \"""
                        hello
                        \""";
                }
            }
            """;
        final var once = Javafmt.format(src);
        final var twice = Javafmt.format(once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void textBlockWithMultipleLines_isIdempotent() {
        final var src = """
            class Foo {

                String greeting() {
                    return \"""
                        hello
                        world
                        from javafmt
                        \""";
                }
            }
            """;
        final var once = Javafmt.format(src);
        final var twice = Javafmt.format(once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void textBlockAsMethodArgument_isIdempotent() {
        // The shape that exposed the bug in real test sources: a text block passed to
        // an assertion helper, several levels of indent into a test method.
        final var src = """
            class FooTest {

                void t() {
                    assertThat(format(\"""
                        import java.util.List;
                        class Foo {}\"""));
                }
            }
            """;
        final var once = Javafmt.format(src);
        final var twice = Javafmt.format(once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void textBlockArgument_staysInlineWithOpenParen() {
        // f(""" must not get split with """ on its own line.
        final var src = """
            class Foo {

                void test() {
                    parse(\"""
                        hello
                        \""");
                }
            }
            """;
        final var formatted = Javafmt.format(src);
        assertThat(formatted).contains("parse(\"\"\"").contains("\"\"\");");
        assertThat(formatted).doesNotContain("parse(\n");
    }

    @Test
    void traditionalStringWithEscapedNewline_isUntouched() {
        // Plain string literals with \n escapes are single-line source — the renderer
        // path that splits on '\n' does not apply, and Verbatim is not needed.
        final var src = """
            class Foo {

                String t() {
                    return "a\\nb";
                }
            }
            """;
        final var once = Javafmt.format(src);
        final var twice = Javafmt.format(once);
        assertThat(twice).isEqualTo(once);
        assertThat(once).contains("\"a\\nb\"");
    }
}
