package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RedundantModifierTest {

    @Test
    void publicAbstractRemovedFromInterfaceMethod() {
        final var unit = JavaParser.parseUnit("""
            interface Fixture {

                public abstract void method();
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        // Both 'public' and 'abstract' are redundant on an interface method
        assertThat(result.edits()).hasSize(2);
        assertThat(result.edits()).allMatch(e -> e.replacement().isEmpty());
        assertThat(LintFixtures.fix(new RedundantModifier(), unit.source())).isEqualTo("interface Fixture {\n\n    void method();\n}\n");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void publicStaticFinalRemovedFromInterfaceField() {
        final var unit = JavaParser.parseUnit("""
            interface Fixture {

                public static final int VALUE = 42;
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(3);
        assertThat(result.edits()).allMatch(e -> e.replacement().isEmpty());
        assertThat(LintFixtures.fix(new RedundantModifier(), unit.source())).isEqualTo("interface Fixture {\n\n    int VALUE = 42;\n}\n");
    }

    @Test
    void nonRedundantInterfaceMethodProducesNoEdits() {
        final var unit = JavaParser.parseUnit("""
            interface Fixture {

                void method();
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void defaultInterfaceMethodPublicKept() {
        // 'public' on a default interface method is redundant, but 'default' itself is not
        final var unit = JavaParser.parseUnit("""
            interface Fixture {

                public default void method() {}
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEmpty();
        assertThat(LintFixtures.fix(new RedundantModifier(), unit.source())).isEqualTo("interface Fixture {\n\n    default void method() {}\n}\n");
    }

    @Test
    void staticInterfaceMethodPublicRemovedAbstractNotApplicable() {
        // static interface methods can have explicit 'public', which is redundant
        final var unit = JavaParser.parseUnit("""
            interface Fixture {

                public static int helper() { return 0; }
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEmpty();
        assertThat(LintFixtures.fix(new RedundantModifier(), unit.source()))
                .isEqualTo("interface Fixture {\n\n    static int helper() { return 0; }\n}\n");
    }

    @Test
    void staticOnNestedInterfaceRemoved() {
        final var unit = JavaParser.parseUnit("""
            class Outer {

                static interface Inner {}
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEmpty();
        assertThat(LintFixtures.fix(new RedundantModifier(), unit.source())).isEqualTo("class Outer {\n\n    interface Inner {}\n}\n");
    }

    @Test
    void staticOnNestedEnumRemoved() {
        final var unit = JavaParser.parseUnit("""
            class Outer {

                static enum Status { A, B }
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEmpty();
        assertThat(LintFixtures.fix(new RedundantModifier(), unit.source())).isEqualTo("class Outer {\n\n    enum Status { A, B }\n}\n");
    }

    @Test
    void finalMethodInFinalClassRemoved() {
        final var unit = JavaParser.parseUnit("""
            final class Fixture {

                public final void method() {}
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEmpty();
        assertThat(LintFixtures.fix(new RedundantModifier(), unit.source())).isEqualTo("final class Fixture {\n\n    public void method() {}\n}\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "final class Fixture {\n    final Fixture() {}\n}\n",
            "final class Fixture {\n    Fixture(int a) {\n        super();\n    }\n}\n",
            "record Fixture(int a) {\n    public Fixture {}\n}\n",
            "final record Fixture(int a) {\n    Fixture {\n        if (a < 0) throw new IllegalArgumentException();\n    }\n}\n",
    })
    void constructorsAreNotTreatedAsFinalMethods(final String source) {
        assertThat(LintFixtures.fix(new RedundantModifier(), source)).isEqualTo(source);
    }

    @Test
    void finalMethodInNonFinalClassPreserved() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                public final void method() {}
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void blockCommentBetweenModifiersStillRemovesBoth() {
        final var unit = JavaParser.parseUnit("""
            interface Fixture {

                public /* c */ abstract void method();
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(2);
        assertThat(result.edits()).allMatch(e -> e.replacement().isEmpty());
        assertThat(LintFixtures.fix(new RedundantModifier(), unit.source())).isEqualTo("interface Fixture {\n\n    /* c */ void method();\n}\n");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void lineCommentAfterAnnotationStillRemovesModifiers() {
        final var unit = JavaParser.parseUnit("""
            interface Fixture {

                @SuppressWarnings("varargs") // creating a stream from an array is safe
                public abstract void method();
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(2);
        assertThat(result.edits()).allMatch(e -> e.replacement().isEmpty());
        assertThat(LintFixtures.fix(new RedundantModifier(), unit.source()))
                .isEqualTo("interface Fixture {\n\n    @SuppressWarnings(\"varargs\") // creating a stream from an array is safe\n    void method();\n}\n");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void lineCommentBeforeTheModifierStillRemovesIt() {
        final var unit = JavaParser.parseUnit("""
            interface Fixture {

                public
                // why this constant exists
                static final int VALUE = 42;
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(3);
        assertThat(LintFixtures.fix(new RedundantModifier(), unit.source()))
                .isEqualTo("interface Fixture {\n\n    \n    // why this constant exists\n    int VALUE = 42;\n}\n");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void anInterfaceFieldGroupIsLeftForTheSplitToHappenFirst() {
        // Each declarator of `public static final int A = 1, B = 2;` is its own member carrying the
        // same ModifiersTree, so the rule emitted the same three deletions once per declarator and
        // applyEdits rejected them as overlapping. java/lang/invoke/MethodHandleInfo.java declares
        // its nine REF_ constants this way and was refused over it.
        final var unit = JavaParser.parseUnit("""
            interface Fixture {

                public static final int A = 1, B = 2;
            }
            """);
        final var result = new RedundantModifier().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }
}
