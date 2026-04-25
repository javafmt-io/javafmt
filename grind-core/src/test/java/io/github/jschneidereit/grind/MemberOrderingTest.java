
package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MemberOrderingTest {

    private static final GrindConfig WITH_ORDERING = new GrindConfig(true);

    @Nested
    class ClassMemberOrdering {

        @Test
        void staticFieldBeforeInstanceField() {
            assertThat(Grind.format("class C { int x; static int MAX = 10; }", WITH_ORDERING))
                .isEqualTo("class C {\n    static int MAX = 10;\n\n    int x;\n}");
        }

        @Test
        void instanceFieldBeforeMethod() {
            assertThat(Grind.format("class C { void go() {} int x; }", WITH_ORDERING))
                .isEqualTo("class C {\n    int x;\n\n    void go() {}\n}");
        }

        @Test
        void methodsOrderedByVisibility() {
            assertThat(Grind.format(
                    "class C { private void priv() {} public void pub() {} protected void prot() {} void pkg() {} }",
                    WITH_ORDERING))
                .isEqualTo("class C {\n    public void pub() {}\n\n    protected void prot() {}\n\n    void pkg() {}\n\n    private void priv() {}\n}");
        }

        @Test
        void staticMethodLast() {
            assertThat(Grind.format("class C { private static void help() {} void go() {} }", WITH_ORDERING))
                .isEqualTo("class C {\n    void go() {}\n\n    private static void help() {}\n}");
        }

        @Test
        void constructorBetweenFieldsAndMethods() {
            assertThat(Grind.format(
                    "class C { public void go() {} C() {} int x; }",
                    WITH_ORDERING))
                .isEqualTo("class C {\n    int x;\n\n    C() {}\n\n    public void go() {}\n}");
        }

        @Test
        void reorderingIsIdempotent() {
            final var input = "class C { private void priv() {} public static int MAX = 10; int x; public void pub() {} }";
            final var formatted = Grind.format(input, WITH_ORDERING);
            assertThat(Grind.format(formatted, WITH_ORDERING)).isEqualTo(formatted);
        }

        @Test
        void defaultConfig_preservesSourceOrder() {
            final var input = "class C { private void priv() {} int x; }";
            final var formatted = Grind.format(input);
            assertThat(formatted.indexOf("priv")).isLessThan(formatted.indexOf("int x"));
        }
    }

    @Nested
    class EnumBodyMemberOrdering {

        @Test
        void bodyMethods_orderedByVisibility() {
            assertThat(Grind.format(
                    "enum Status { ACTIVE; private void priv() {} public void pub() {} }",
                    WITH_ORDERING))
                .isEqualTo("enum Status {\n    ACTIVE;\n\n    public void pub() {}\n\n    private void priv() {}\n}");
        }

        @Test
        void reorderingIsIdempotent() {
            final var input = "enum Status { ACTIVE; private void priv() {} public void pub() {} }";
            final var formatted = Grind.format(input, WITH_ORDERING);
            assertThat(Grind.format(formatted, WITH_ORDERING)).isEqualTo(formatted);
        }
    }

    @Nested
    class SealedClassMemberOrdering {

        @Test
        void nestedTypesPinnedTop_aheadOfStaticFields() {
            final var input = "sealed interface Shape permits Circle, Square { static final int DIM = 2; final class Circle implements Shape {} final class Square implements Shape {} }";
            final var formatted = Grind.format(input, WITH_ORDERING);
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
            final var result = Grind.formatWithResult(input, WITH_ORDERING);
            assertThat(result.output().indexOf("static final int A"))
                .isLessThan(result.output().indexOf("static final int B"));
            assertThat(result.output().indexOf("static final int B"))
                .isLessThan(result.output().indexOf("priv"));
        }

        @Test
        void forwardReferenceBetweenStaticFinals_emitsWarning() {
            final var input = "class C { static final int A = B; static final int B = 1; }";
            final var result = Grind.formatWithResult(input, WITH_ORDERING);
            assertThat(result.diagnostics())
                .filteredOn(d -> !d.isError())
                .extracting(Diagnostic::message)
                .anySatisfy(m -> assertThat(m).contains("forward reference").contains("'B'").contains("'A'"));
        }

        @Test
        void noForwardReference_stillReorders() {
            final var input = "class C { private void priv() {} static final int A = 1; }";
            final var result = Grind.formatWithResult(input, WITH_ORDERING);
            assertThat(result.output().indexOf("static final int A"))
                .isLessThan(result.output().indexOf("priv"));
            assertThat(result.diagnostics()).isEmpty();
        }
    }

    @Nested
    class RecordBodyMemberOrdering {

        @Test
        void bodyMethods_orderedByVisibility() {
            assertThat(Grind.format(
                    "record R(int x) { private void priv() {} public void pub() {} }",
                    WITH_ORDERING))
                .isEqualTo("record R(int x) {\n    public void pub() {}\n\n    private void priv() {}\n}");
        }

        @Test
        void reorderingIsIdempotent() {
            final var input = "record R(int x) { private void priv() {} public void pub() {} }";
            final var formatted = Grind.format(input, WITH_ORDERING);
            assertThat(Grind.format(formatted, WITH_ORDERING)).isEqualTo(formatted);
        }
    }
}
