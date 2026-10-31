package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FinalForEachVariableTest {

    static Stream<Arguments> rewritten() {
        return Stream.of(
                Arguments.of("explicit type", "for (String s : xs) { use(s); }", "for (final String s : xs) { use(s); }"),
                Arguments.of("var", "for (var s : xs) { use(s); }", "for (final var s : xs) { use(s); }"),
                Arguments.of("unnamed var", "for (var _ : xs) { tick(); }", "for (final var _ : xs) { tick(); }"),
                Arguments.of(
                        "generic type",
                        "for (Map.Entry<String, Integer> e : xs) { use(e); }",
                        "for (final Map.Entry<String, Integer> e : xs) { use(e); }"),
                Arguments.of("annotated variable", "for (@Audited String s : xs) { use(s); }", "for (@Audited final String s : xs) { use(s); }"),
                Arguments.of(
                        "same-named write in a nested lambda",
                        "for (String s : xs) { run(() -> { String s = \"x\"; s = s.trim(); }); }",
                        "for (final String s : xs) { run(() -> { String s = \"x\"; s = s.trim(); }); }"),
                Arguments.of(
                        "nested loops",
                        "for (var a : xs) { for (var b : a) { use(b); } }",
                        "for (final var a : xs) { for (final var b : a) { use(b); } }"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rewritten")
    void aForEachVariableGainsFinal(final String label, final String loop, final String expected) {
        assertThat(fix(inMethod(loop))).isEqualTo(inMethod(expected));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rewritten")
    void theFixedSourceNeedsNoFurtherFix(final String label, final String loop, final String expected) {
        final var unit = JavaParser.parseUnit(inMethod(expected));
        assertThat(new FinalForEachVariable().apply(LintContext.parseOnly(unit)).edits()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource( strings = { "for (final String s : xs) { use(s); }", "for (String s : xs) { s = s.trim(); use(s); }", "for (@Audited final String s : xs) {}", "for (int i = 0; i < 3; i++) { use(i); }", "for (var s : xs) { s = null; }", "for (@Audited var s : xs) { use(s); }", })
    void aFinalOrReassignedOrBasicLoopVariableIsLeftAlone(final String loop) {
        assertThat(fix(inMethod(loop))).isEqualTo(inMethod(loop));
    }

    private static String inMethod(final String loop) {
        return "class C {\n    void m() {\n        " + loop + "\n    }\n}\n";
    }

    private static String fix(final String source) {
        final var unit = JavaParser.parseUnit(source);
        return LintEngine.applyEdits(source, new FinalForEachVariable().apply(LintContext.parseOnly(unit)).edits());
    }
}
