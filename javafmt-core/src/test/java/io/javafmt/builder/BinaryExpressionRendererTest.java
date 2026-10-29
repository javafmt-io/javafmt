package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;
import org.junit.jupiter.api.Test;

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
    void threeWayAndOverEighty_allOperatorsBreak() {
        // Mirror of the method-chain heuristic: when an &&/|| chain has >= 3 operands and
        // its flat width exceeds 80 chars, break before every operator so each operand sits
        // on its own line, rather than only breaking the last operator to hit 150.
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

    @Test
    void twoWayAndOverEighty_staysFlat() {
        // Two-operand chains skip the 80-char threshold for the same reason 2-link method
        // chains do: breaking just moves one operand down a line without untangling structure.
        // They still break if they would overflow the 150-char line width.
        final var input = """
            class X {

                boolean ok(Object a, Object b) {
                    final var ok = a.longMethodName(arg1) && b.anotherLongMethod(arg2, arg3, arg4);
                }
            }""".stripIndent();
        final var expected = """
            class X {

                boolean ok(Object a, Object b) {
                    final var ok = a.longMethodName(arg1) && b.anotherLongMethod(arg2, arg3, arg4);
                }
            }""".stripIndent();
        assertThat(format(input)).isEqualTo(expected);
    }

    @Test
    void threeWayAndUnderEighty_staysFlat() {
        // Three operands but the chain fits in 80 chars — stays flat. The threshold matters,
        // not just the operand count.
        final var input = "class X { boolean ok(boolean a, boolean b, boolean c) { return a && b && c; } }";
        final var expected = """
            class X {

                boolean ok(boolean a, boolean b, boolean c) {
                    return a && b && c;
                }
            }""".stripIndent();
        assertThat(format(input)).isEqualTo(expected);
    }

    @Test
    void brokenLogicalChain_innerChain_staysFlat_evenWhenSourceBrokeIt() {
        // When the outer && chain breaks (>= 3 operands, flat width > 80), each operand
        // renders as if line width = 150 — author-preserve and the 80-char chain heuristic
        // are suppressed for nested chains. This keeps the broken logical-chain visually
        // 3-line clean instead of cascading into the source's prior layout artifacts.
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
