package io.javafmt.builder;

import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BinaryExpressionRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source)))
                .stripTrailing();
    }

    @Test
    void andOnOneLine_staysOnOneLine() {
        final var input = "class X { boolean ok(boolean a, boolean b) { return a && b; } }";
        final var expected = """
            class X {

                boolean ok(boolean a, boolean b) {return a && b;}
            }""".stripIndent();
        assertThat(format(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("authorBrokenChainsThatFit")
    void authorBrokenLogicalChainThatFitsTheLine_joins(final String broken, final String joined) {
        final var template = "class X {\n\n    boolean ok(Object imp, Object ms, boolean a, boolean b, boolean c) {\n        %s\n    }\n}";
        assertThat(format(template.formatted(broken))).isEqualTo(template.formatted(joined));
    }

    static Stream<Arguments> authorBrokenChainsThatFit() {
        return Stream.of(
                Arguments.of(
                        "return imp instanceof MemberSelectTree\n            && ms.getIdentifier().contentEquals(\"*\");",
                        "return imp instanceof MemberSelectTree && ms.getIdentifier().contentEquals(\"*\");"),
                Arguments.of("return a\n            || b;", "return a || b;"),
                Arguments.of("return a\n            && b\n            && c;", "return a && b && c;"));
    }

    @Test
    void threeWayAndOverTheLine_allOperatorsBreak() {
        // When an &&/|| chain does not fit the 150-char line, break before every operator so
        // each operand sits on its own line, rather than only breaking the last operator.
        final var input = """
            class X {

                boolean ok(Object annotations, Object recursor) {
                    final var inline = annotations.size() == 1 && annotations.getFirst().getArguments().isEmpty() && !recursor.isFollowedByNewline(annotations.getFirst());
                }
            }""".stripIndent();
        final var expected = """
            class X {

                boolean ok(Object annotations, Object recursor) {
                    final var inline = annotations.size() == 1
                        && annotations.getFirst().getArguments().isEmpty()
                        && !recursor.isFollowedByNewline(annotations.getFirst());
                }
            }""".stripIndent();
        assertThat(format(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "final var ok = a.longMethodName(arg1) && b.anotherLongMethod(arg2, arg3, arg4);",
            "return a && b && c;",
            "final var ok = tree != null && !(tree instanceof BinaryTree) && !(tree instanceof MethodInvocationTree) && !(tree instanceof LambdaTree);",
    })
    void logicalChainThatFitsTheLine_staysFlat(final String statement) {
        final var source = "class X {\n\n    boolean ok(Object a, Object b, Object tree) {\n        " + statement + "\n    }\n}";
        assertThat(format(source)).isEqualTo(source);
    }

    @Test
    void brokenLogicalChain_innerChain_staysFlat_evenWhenSourceBrokeIt() {
        final var input = """
            class X {

                void test(Object annotations, Object recursor) {
                    final var inline = annotations.size() == 1 && annotations.getFirst()
                            .getArguments()
                            .isEmpty() && !recursor.isFollowedByNewline(annotations.getFirst());
                }
            }""".stripIndent();
        final var expected = """
            class X {

                void test(Object annotations, Object recursor) {
                    final var inline = annotations.size() == 1
                        && annotations.getFirst().getArguments().isEmpty()
                        && !recursor.isFollowedByNewline(annotations.getFirst());
                }
            }""".stripIndent();
        assertThat(format(input)).isEqualTo(expected);
    }

    @Test
    void otherBinaryOperators_unaffected() {
        final var input = "class X { int n(int a, int b) { return a + b * 2; } }";
        final var expected = """
            class X {

                int n(int a, int b) {return a + b * 2;}
            }""".stripIndent();
        assertThat(format(input)).isEqualTo(expected);
    }
}
