package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FinalCatchParameterTest {

    static Stream<Arguments> rewritten() {
        return Stream.of(
                Arguments.of("single type", "catch (IOException e) { log(e); }", "catch (final IOException e) { log(e); }"),
                Arguments.of(
                        "multi-catch",
                        "catch (IllegalStateException | IllegalArgumentException e) {}",
                        "catch (final IllegalStateException | IllegalArgumentException e) {}"),
                Arguments.of("unnamed parameter", "catch (IllegalStateException _) {}", "catch (final IllegalStateException _) {}"),
                Arguments.of("annotated parameter", "catch (@Annotated IOException e) {}", "catch (@Annotated final IOException e) {}"),
                Arguments.of("qualified type", "catch (java.io.IOException e) { log(e); }", "catch (final java.io.IOException e) { log(e); }"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rewritten")
    void aCatchParameterGainsFinal(final String label, final String catchClause, final String expected) {
        assertThat(fix(inMethod(catchClause))).isEqualTo(inMethod(expected));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rewritten")
    void theFixedSourceNeedsNoFurtherFix(final String label, final String catchClause, final String expected) {
        final var unit = JavaParser.parseUnit(inMethod(expected));
        assertThat(new FinalCatchParameter().apply(LintContext.parseOnly(unit)).edits()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "catch (final IOException e) {}", "catch (IOException e) { e = null; }", "catch (@Annotated final IOException e) {}", })
    void aFinalOrReassignedCatchParameterIsLeftAlone(final String catchClause) {
        assertThat(fix(inMethod(catchClause))).isEqualTo(inMethod(catchClause));
    }

    private static String inMethod(final String catchClause) {
        return "class C {\n    void m() {\n        try { run(); } " + catchClause + "\n    }\n}\n";
    }

    private static String fix(final String source) {
        final var unit = JavaParser.parseUnit(source);
        return LintEngine.applyEdits(source, new FinalCatchParameter().apply(LintContext.parseOnly(unit)).edits());
    }
}
