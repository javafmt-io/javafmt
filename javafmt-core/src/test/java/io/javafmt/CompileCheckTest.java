package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompileCheckTest {

    @Test
    void validSourceCompiles() {
        assertThat(CompileCheck.check("class A {\n    int x = 1;\n}\n")).isInstanceOf(CompileCheck.Outcome.Compiles.class);
    }

    @Test
    void publicTypeCompilesDespiteTheSyntheticFileName() {
        assertThat(CompileCheck.check("public class Widget {\n    int x = 1;\n}\n")).isInstanceOf(CompileCheck.Outcome.Compiles.class);
    }

    @Test
    void uninitializedFinalFieldIsBroken() {
        // The ExplicitInitialization break from PLAN P0.3: stripping `= 0` off a final field.
        assertThat(CompileCheck.check("class A {\n    static final int LIMIT;\n}\n"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(CompileCheck.Outcome.Broken.class))
                .extracting(CompileCheck.Outcome.Broken::errors)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .anyMatch(e -> e.contains("might not have been initialized"));
    }

    @Test
    void typeMismatchFromAWidenedVarIsBroken() {
        // The LocalVarUseVar diamond break from PLAN P0.3.
        final var source = """
                import java.util.ArrayList;

                class A {
                    void m() {
                        ArrayList<Object> xs = new ArrayList<>();
                        String s = xs.getFirst();
                    }
                }
                """;
        assertThat(CompileCheck.check(source)).isInstanceOf(CompileCheck.Outcome.Broken.class);
    }

    @Test
    void lambdaInitializerThatCannotBeInferredIsBroken() {
        assertThat(CompileCheck.check("class A {\n    void m() {\n        var r = () -> {};\n    }\n}\n"))
                .isInstanceOf(CompileCheck.Outcome.Broken.class);
    }

    @Test
    void sourceWithUnresolvableReferencesIsSkippedRatherThanBroken() {
        // Most fixtures are snippets referencing types that aren't on the classpath. Those
        // can't be judged, so they are skipped rather than producing false positives.
        assertThat(CompileCheck.check("class A {\n    void m() {\n        Unknown.thing().chain();\n    }\n}\n"))
                .isInstanceOf(CompileCheck.Outcome.Skipped.class);
    }

    @Test
    void moduleDeclarationIsSkipped() {
        assertThat(CompileCheck.check("module com.example.app {\n    requires java.base;\n}\n"))
                .isInstanceOf(CompileCheck.Outcome.Skipped.class);
    }

    @Test
    void unparseableSourceIsSkippedBecauseParsingIsCheckedElsewhere() {
        assertThat(CompileCheck.check("class A { void m( }")).isInstanceOf(CompileCheck.Outcome.Skipped.class);
    }

    @Test
    void blankSourceIsSkipped() {
        assertThat(CompileCheck.check("   \n")).isInstanceOf(CompileCheck.Outcome.Skipped.class);
    }
}
