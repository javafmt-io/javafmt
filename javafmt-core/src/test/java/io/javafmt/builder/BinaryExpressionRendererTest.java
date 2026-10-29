package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BinaryExpressionRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source))).stripTrailing();
    }

    @Test
    void andOnOneLine_staysOnOneLine() {
        final var input = "class X { boolean ok(boolean a, boolean b) { return a && b; } }";
        final var expected = """
            class X {

                boolean ok(boolean a, boolean b) {
                    return a && b;
                }
            }""".stripIndent();
        assertThat(format(input)).isEqualTo(expected);
    }

    @Test
    void authorBrokeBeforeAnd_isPreservedAtContinuationIndent() {
        final var input = """
            class X {

                boolean ok(Object imp, Object ms) {
                    return imp instanceof MemberSelectTree
                        && ms.getIdentifier().contentEquals("*");
                }
            }""".stripIndent();
        final var expected = """
            class X {

                boolean ok(Object imp, Object ms) {
                    return imp instanceof MemberSelectTree
                        && ms.getIdentifier().contentEquals("*");
                }
            }""".stripIndent();
        assertThat(format(input)).isEqualTo(expected);
    }

    @Test
    void authorBrokeBeforeOr_isPreserved() {
        final var input = """
            class X {

                boolean ok(boolean a, boolean b) {
                    return a
                        || b;
                }
            }""".stripIndent();
        final var expected = """
            class X {

                boolean ok(boolean a, boolean b) {
                    return a
                        || b;
                }
            }""".stripIndent();
        assertThat(format(input)).isEqualTo(expected);
    }

    @Test
    void threeWayAndChain_allBreaksPreservedAtSameIndent() {
        final var input = """
            class X {

                boolean ok(boolean a, boolean b, boolean c) {
                    return a
                        && b
                        && c;
                }
            }""".stripIndent();
        final var expected = """
            class X {

                boolean ok(boolean a, boolean b, boolean c) {
                    return a
                        && b
                        && c;
                }
            }""".stripIndent();
        assertThat(format(input)).isEqualTo(expected);
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
    @ValueSource(strings = {"final var ok = a.longMethodName(arg1) && b.anotherLongMethod(arg2, arg3, arg4);", "return a && b && c;", "final var ok = tree != null && !(tree instanceof BinaryTree) && !(tree instanceof MethodInvocationTree) && !(tree instanceof LambdaTree);"})
    void logicalChainThatFitsTheLine_staysFlat(final String statement) {
        final var source = "class X {\n\n    boolean ok(Object a, Object b, Object tree) {\n        " + statement + "\n    }\n}";
        assertThat(format(source)).isEqualTo(source);
    }

    @Test
    void brokenLogicalChain_innerChain_staysFlat_evenWhenSourceBrokeIt() {
        // When the outer && chain breaks, each operand renders with author-preserve suppressed
        // for nested chains. This keeps the broken logical-chain visually 3-line clean instead
        // of cascading into the source's prior layout artifacts.
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
        // Only && and || get the author-preserve treatment. Arithmetic operators render flat.
        final var input = "class X { int n(int a, int b) { return a + b * 2; } }";
        final var expected = """
            class X {

                int n(int a, int b) {
                    return a + b * 2;
                }
            }""".stripIndent();
        assertThat(format(input)).isEqualTo(expected);
    }
}
