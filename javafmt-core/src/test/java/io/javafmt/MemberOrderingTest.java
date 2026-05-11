
package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.javafmt.parser.JavaParser;

import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MemberOrderingTest {

    private static final JavafmtConfig WITH_ORDERING = new JavafmtConfig(true);

    @ParameterizedTest(name = "{0}")
    @MethodSource("reorderingCases")
    void reorderingProducesExactExpected_andReparses(
            final String label, final String input, final String expected) {
        final var formatted = Javafmt.format(input, WITH_ORDERING);
        assertThat(formatted).as("reordered output for '%s'", label).isEqualTo(expected);
        assertThatCode(() -> JavaParser.parseUnit(formatted))
            .as("reordered output for '%s' must re-parse without errors", label)
            .doesNotThrowAnyException();
    }

    static Stream<Arguments> reorderingCases() {
        return Stream.of(
            Arguments.of(
                "instance-field-before-static-field",
                "class C { int x; static int MAX = 10; }",
                "class C {\n    static int MAX = 10;\n\n    int x;\n}"),
            Arguments.of(
                "method-before-instance-field",
                "class C { void go() {} int x; }",
                "class C {\n    int x;\n\n    void go() {}\n}"),
            Arguments.of(
                "constructor-after-methods",
                "class C { public void go() {} C() {} int x; }",
                "class C {\n    int x;\n\n    C() {}\n\n    public void go() {}\n}"),
            Arguments.of(
                "methods-in-reverse-visibility-order",
                "class C { private void priv() {} public void pub() {} protected void prot() {} void pkg() {} }",
                "class C {\n    public void pub() {}\n\n    protected void prot() {}\n\n    void pkg() {}\n\n    private void priv() {}\n}"),
            Arguments.of(
                "static-method-before-instance-method",
                "class C { private static void help() {} void go() {} }",
                "class C {\n    void go() {}\n\n    private static void help() {}\n}")
        );
    }

    @Nested
    class ClassMemberOrdering {

        @Test
        void staticFieldBeforeInstanceField() {
            assertThat(Javafmt.format("class C { int x; static int MAX = 10; }", WITH_ORDERING))
                .isEqualTo("class C {\n    static int MAX = 10;\n\n    int x;\n}");
        }

        @Test
        void instanceFieldBeforeMethod() {
            assertThat(Javafmt.format("class C { void go() {} int x; }", WITH_ORDERING))
                .isEqualTo("class C {\n    int x;\n\n    void go() {}\n}");
        }

        @Test
        void methodsOrderedByVisibility() {
            assertThat(Javafmt.format(
                    "class C { private void priv() {} public void pub() {} protected void prot() {} void pkg() {} }",
                    WITH_ORDERING))
                .isEqualTo("class C {\n    public void pub() {}\n\n    protected void prot() {}\n\n    void pkg() {}\n\n    private void priv() {}\n}");
        }

        @Test
        void staticMethodLast() {
            assertThat(Javafmt.format("class C { private static void help() {} void go() {} }", WITH_ORDERING))
                .isEqualTo("class C {\n    void go() {}\n\n    private static void help() {}\n}");
        }

        @Test
        void constructorBetweenFieldsAndMethods() {
            assertThat(Javafmt.format(
                    "class C { public void go() {} C() {} int x; }",
                    WITH_ORDERING))
                .isEqualTo("class C {\n    int x;\n\n    C() {}\n\n    public void go() {}\n}");
        }

        @Test
        void reorderingIsIdempotent() {
            final var input = "class C { private void priv() {} public static int MAX = 10; int x; public void pub() {} }";
            final var formatted = Javafmt.format(input, WITH_ORDERING);
            assertThat(Javafmt.format(formatted, WITH_ORDERING)).isEqualTo(formatted);
        }

        @Test
        void defaultConfig_preservesSourceOrder() {
            final var input = "class C { private void priv() {} int x; }";
            final var formatted = Javafmt.format(input);
            assertThat(formatted.indexOf("priv")).isLessThan(formatted.indexOf("int x"));
        }
    }

    @Nested
    class EnumBodyMemberOrdering {

        @Test
        void bodyMethods_orderedByVisibility() {
            assertThat(Javafmt.format(
                    "enum Status { ACTIVE; private void priv() {} public void pub() {} }",
                    WITH_ORDERING))
                .isEqualTo("enum Status {\n    ACTIVE;\n\n    public void pub() {}\n\n    private void priv() {}\n}");
        }

        @Test
        void reorderingIsIdempotent() {
            final var input = "enum Status { ACTIVE; private void priv() {} public void pub() {} }";
            final var formatted = Javafmt.format(input, WITH_ORDERING);
            assertThat(Javafmt.format(formatted, WITH_ORDERING)).isEqualTo(formatted);
        }
    }

    @Nested
    class SealedClassMemberOrdering {

        @Test
        void nestedTypesPinnedTop_aheadOfStaticFields() {
            final var input = "sealed interface Shape permits Circle, Square { static final int DIM = 2; final class Circle implements Shape {} final class Square implements Shape {} }";
            final var formatted = Javafmt.format(input, WITH_ORDERING);
            assertThat(formatted.indexOf("Circle"))
                .isLessThan(formatted.indexOf("DIM"));
            assertThat(formatted.indexOf("Square"))
                .isLessThan(formatted.indexOf("DIM"));
        }
    }

    @Nested
    class ForwardReferenceSafety {

        @Test
        void forwardReferenceBetweenStaticFinals_preservesSourceOrder() {
            final var input = "class C { static final int A = B; static final int B = 1; private void priv() {} }";
            final var result = Javafmt.formatWithResult(input, WITH_ORDERING);
            assertThat(result.output().indexOf("static final int A"))
                .isLessThan(result.output().indexOf("static final int B"));
            assertThat(result.output().indexOf("static final int B"))
                .isLessThan(result.output().indexOf("priv"));
        }

        @Test
        void forwardReferenceBetweenStaticFinals_emitsWarning() {
            final var input = "class C { static final int A = B; static final int B = 1; }";
            final var result = Javafmt.formatWithResult(input, WITH_ORDERING);
            assertThat(result.diagnostics())
                .filteredOn(d -> !d.isError())
                .extracting(Diagnostic::message)
                .anySatisfy(m -> assertThat(m).contains("forward reference").contains("'B'").contains("'A'"));
        }

        @Test
        void noForwardReference_stillReorders() {
            final var input = "class C { private void priv() { return; } static final int A = 1; }";
            final var result = Javafmt.formatWithResult(input, WITH_ORDERING);
            assertThat(result.output().indexOf("static final int A"))
                .isLessThan(result.output().indexOf("priv"));
            assertThat(result.diagnostics()).isEmpty();
        }
    }

    @Nested
    class RecordBodyMemberOrdering {

        @Test
        void bodyMethods_orderedByVisibility() {
            assertThat(Javafmt.format(
                    "record R(int x) { private void priv() {} public void pub() {} }",
                    WITH_ORDERING))
                .isEqualTo("record R(int x) {\n    public void pub() {}\n\n    private void priv() {}\n}");
        }

        @Test
        void reorderingIsIdempotent() {
            final var input = "record R(int x) { private void priv() {} public void pub() {} }";
            final var formatted = Javafmt.format(input, WITH_ORDERING);
            assertThat(Javafmt.format(formatted, WITH_ORDERING)).isEqualTo(formatted);
        }
    }
}
