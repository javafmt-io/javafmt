package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class UnusedVariableShouldBeUnnamedTest {

    static Stream<Arguments> renamed() {
        return Stream.of(
                Arguments.of(
                        "catch parameter",
                        "try { run(); } catch (IllegalStateException e) { log(); }",
                        "try { run(); } catch (IllegalStateException _) { log(); }"),
                Arguments.of("implicit lambda parameter", "use(x -> 1);", "use(_ -> 1);"),
                Arguments.of("one of several lambda parameters", "use((a, b) -> b);", "use((_, b) -> b);"),
                Arguments.of("explicitly typed lambda parameter", "use((String s) -> 1);", "use((String _) -> 1);"),
                Arguments.of("enhanced-for variable", "for (var item : items) { count++; }", "for (var _ : items) { count++; }"),
                Arguments.of("local with an initializer", "var ignored = compute(); log();", "var _ = compute(); log();"),
                Arguments.of("final local with an initializer", "final int ignored = compute();", "final int _ = compute();"),
                Arguments.of("try-with-resources variable", "try (var lock = acquire()) { work(); }", "try (var _ = acquire()) { work(); }"),
                Arguments.of("instanceof binding", "if (o instanceof String s) { log(); }", "if (o instanceof String _) { log(); }"),
                Arguments.of(
                        "switch pattern binding",
                        "switch (o) { case String s -> log(); default -> {} }",
                        "switch (o) { case String _ -> log(); default -> {} }"),
                Arguments.of(
                        "record pattern component",
                        "if (o instanceof Point(int x, int y)) { use(y); }",
                        "if (o instanceof Point(int _, int y)) { use(y); }"),
                Arguments.of(
                        "local whose name appears inside an annotation string",
                        "@SuppressWarnings(\"ignored\") var ignored = compute();",
                        "@SuppressWarnings(\"ignored\") var _ = compute();"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("renamed")
    void anUnusedVariableIsRenamedToAnUnderscore(final String label, final String body, final String expected) {
        assertThat(fix(inMethod(body))).isEqualTo(inMethod(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "try { run(); } catch (IllegalStateException e) { log(e); }",
            "use(x -> x);",
            "for (var item : items) { use(item); }",
            "var used = compute(); use(used);",
            "try (var lock = acquire()) { use(lock); }",
            "if (o instanceof String s) { use(s); }",
            "int unassigned; log();",
            "int a = 1, b = 2;",
            "for (int i = 0; ; ) { log(); }",
            "var _ = compute();",
            "String names[] = new String[] {\"a\"};",
            "int grid [][] = new int[1][1];",
            "var captured = compute(); use(() -> captured);",
            "var shadowed = compute(); use(shadowed -> shadowed);",
            "var called = compute(); called();",
    })
    void aVariableThatIsUsedOrCannotBeUnnamedIsLeftAlone(final String body) {
        assertThat(fix(inMethod(body))).isEqualTo(inMethod(body));
    }

    @ParameterizedTest
    @ValueSource(strings = {"class C { int unusedField = 1; }", "class C { void m(String unusedParameter) {} }", "record R(int unusedComponent) {}"})
    void fieldsParametersAndRecordComponentsAreLeftAlone(final String source) {
        assertThat(fix(source)).isEqualTo(source);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("renamed")
    void sourceTargetingAReleaseWithoutUnnamedVariablesKeepsItsNames(final String label, final String body, final String expected) {
        final var unit = JavaParser.parseUnit(inMethod(body));
        assertThat(
                        new UnusedVariableShouldBeUnnamed().apply(
                                        LintContext.parseOnly(unit).withRelease(21))
                                .edits())
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("renamed")
    void theFixedSourceNeedsNoFurtherFix(final String label, final String body, final String expected) {
        final var unit = JavaParser.parseUnit(inMethod(expected));
        assertThat(
                        new UnusedVariableShouldBeUnnamed().apply(LintContext.parseOnly(unit))
                                .edits())
                .isEmpty();
    }

    private static String inMethod(final String body) {
        return "class C {\n    void m(Object o, Iterable<String> items) {\n        " + body + "\n    }\n}\n";
    }

    private static String fix(final String source) {
        final var unit = JavaParser.parseUnit(source);
        return LintEngine.applyEdits(source, new UnusedVariableShouldBeUnnamed().apply(LintContext.parseOnly(unit))
                .edits());
    }
}
