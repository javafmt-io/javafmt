package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExplicitInitializationTest {

    @ParameterizedTest
    @ValueSource(strings = {"class Fixture { int x = 0; }", "class Fixture { byte x = 0; }", "class Fixture { short x = 0; }", "class Fixture { boolean x = false; }", "class Fixture { Object x = null; }", "class Fixture { String x = null; }"})
    void redundantDefaultInitializerRemovedFromField(final String src) {
        final var unit = JavaParser.parseUnit(src);
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void longFieldWithZeroLRemovedFromField() {
        final var unit = JavaParser.parseUnit("class Fixture { long x = 0L; }");
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEmpty();
    }

    @Test
    void doubleFieldWithZeroDotZeroRemovedFromField() {
        final var unit = JavaParser.parseUnit("class Fixture { double x = 0.0; }");
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEmpty();
    }

    @Test
    void floatFieldWithZeroFRemovedFromField() {
        final var unit = JavaParser.parseUnit("class Fixture { float x = 0.0f; }");
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEmpty();
    }

    @Test
    void nonDefaultValueFieldIsPreserved() {
        final var unit = JavaParser.parseUnit("class Fixture { int x = 1; }");
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void nonNullReferenceInitializerIsPreserved() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                String name = "hello";
            }
            """);
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void localVariableDefaultInitNotRemoved() {
        // Rule only applies to fields, not locals
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    int x = 0;
                }
            }
            """);
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void staticFieldWithDefaultInitIsRemoved() {
        final var unit = JavaParser.parseUnit("class Fixture { static int count = 0; }");
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
    }

    @Test
    void arrayFieldWithNullInitIsRemoved() {
        final var unit = JavaParser.parseUnit("class Fixture { int[] data = null; }");
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
    }

    @Test
    void finalFieldKeepsItsInitializer() {
        // `static final int LIMIT;` without an initializer does not compile.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                static final int LIMIT = 0;
            }
            """);
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void interfaceFieldKeepsItsInitializer() {
        // Interface fields are implicitly static final, so stripping the initializer breaks them.
        final var unit = JavaParser.parseUnit("""
            interface Fixture {

                int LIMIT = 0;
            }
            """);
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void annotationMemberDefaultIsLeftAlone() {
        final var unit = JavaParser.parseUnit("""
            @interface Fixture {

                int LIMIT = 0;
            }
            """);
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void equalsSignIsFoundWithoutScanningPastTheDeclaration() {
        // The backward scan for `=` must stop at the declaration start rather than running to the
        // top of the file and latching onto an unrelated `=`.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                int first = 1;

                @Deprecated
                private String name = null;
            }
            """);
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        final var source = new StringBuilder(unit.source());
        final var edit = result.edits().getFirst();
        source.replace(edit.start(), edit.end(), edit.replacement());
        assertThat(source.toString())
                .contains("private String name;")
                .contains("int first = 1;");
    }

    @Test
    void fieldOfAClassWithASuperclassKeepsItsExplicitDefault() {
        // Instance field initializers run after the superclass constructor. If that constructor
        // calls an overridable method which assigns the field, `= 0` is a real write and dropping
        // it changes what the program observes: new Sub().x() returns 0 before and 5 after.
        final var unit = JavaParser.parseUnit("""
            class Fixture extends Base {

                private int x = 0;
            }
            """);
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void equalsSignInsideACommentIsNotMistakenForTheAssignment() {
        // The backward scan for `=` used to stop at the one inside the comment and delete from
        // there, leaving an unterminated comment that came back as the *user's* parse error.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                private int x = /* default = zero */ 0;
            }
            """);
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void commentBeforeTheAssignmentDoesNotBlockTheRewrite() {
        // The comment sits outside the span being deleted, so there is nothing to lose.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                private /* why */ int x = 0;
            }
            """);
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(LintEngine.applyEdits(unit.source(), result.edits())).contains("private /* why */ int x;");
    }

    @Test
    void aCharFieldInitialisedToTheNullCharacterIsStripped() {
        // A char's default is written either way, and '\\0' is the more common of the two.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                private char marker = '\\0';
            }
            """);
        final var result = new ExplicitInitialization().apply(LintContext.parseOnly(unit));
        assertThat(LintEngine.applyEdits(unit.source(), result.edits())).contains("private char marker;");
    }

    @Test
    void aCharFieldInitialisedToSomethingElseIsLeftAlone() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                private char marker = 'x';
            }
            """);
        assertThat(new ExplicitInitialization().apply(LintContext.parseOnly(unit)).edits()).isEmpty();
    }
}
