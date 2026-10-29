package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModifierOrderTest {

    @Test
    void staticPublicReorderedToPublicStatic() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                static public int x = 0;
            }
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("public static");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void finalStaticReorderedToStaticFinal() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                final static int X = 1;
            }
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("static final");
    }

    @Test
    void abstractPublicMethodReorderedToPublicAbstract() {
        final var unit = JavaParser.parseUnit("""
            abstract class Fixture {

                abstract public void method();
            }
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("public abstract");
    }

    @ParameterizedTest
    @ValueSource(strings = {"class Fixture { public static int x = 0; }", "class Fixture { public final int x = 0; }", "class Fixture { private static int x = 0; }", "abstract class Fixture { public abstract void method(); }"})
    void alreadyCanonicalOrderProducesNoEdits(final String src) {
        final var unit = JavaParser.parseUnit(src);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void singleModifierProducesNoEdits() {
        final var unit = JavaParser.parseUnit("class Fixture { public int x = 0; }");
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void nativeSynchronizedReorderedToSynchronizedNative() {
        // JLS canonical: synchronized (index 9) comes before native (index 10)
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                native synchronized void method();
            }
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("synchronized native");
    }

    @Test
    void aBlockCommentAmongTheModifiersIsCarriedThrough() {
        // The scan used to stop here, which left the source disagreeing with what the renderer
        // prints. The comment rides along instead, between the annotations and the keywords.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                static public /* c */ final int X = 1;
            }
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("/* c */ public static final");
    }

    @Test
    void aLineCommentAmongTheModifiersStopsTheScan() {
        // Re-emitting a `//` comment inside the rewritten run would swallow everything after it.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                static public // c
                        final int X = 1;
            }
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(result.edits())
                .singleElement()
                .satisfies(e -> assertThat(e.replacement()).isEqualTo("public static"));
    }

    @Test
    void sealedAbstractReorderedToAbstractSealed() {
        // JLS canonical order (see JavaModifiers.JLS_ORDER): abstract precedes sealed.
        final var unit = JavaParser.parseUnit("""
            sealed abstract class Fixture permits Impl {}

            final class Impl extends Fixture {}
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("abstract sealed");
    }

    @Test
    void anAnnotatedDeclarationIsReorderedLikeAnyOther() {
        // The rule used to give up on any declaration with an annotation, which is most of them.
        // The renderer normalises the order anyway, so skipping them left the post-lint source
        // disagreeing with the printed output — invisible while the verifier compared token
        // multisets, and a refusal once it compares sequences.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                @Deprecated
                static private final int VALUE = 1;
            }
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(LintEngine.applyEdits(unit.source(), result.edits())).contains("private static final int VALUE");
    }

    @Test
    void anAnnotationBetweenTwoKeywordsIsHoistedInFrontOfThem() {
        // The renderer prints annotations before keywords whatever the source says, so the source
        // has to say it too — otherwise the verifier sees tokens in an order the output never has.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                static @Deprecated private final int VALUE = 1;
            }
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(LintEngine.applyEdits(unit.source(), result.edits())).contains("@Deprecated private static final int VALUE");
    }

    @Test
    void aQualifiedAnnotationSurvivesTheHoist() {
        // `@jdk.internal.RequiresIdentity` is one annotation; a name scan that stopped at the first
        // `.` left the rest inside the span being replaced, and it was deleted.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                static @jdk.internal.RequiresIdentity private final int VALUE = 1;
            }
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(LintEngine.applyEdits(unit.source(), result.edits())).contains("@jdk.internal.RequiresIdentity private static final int VALUE");
    }

    @Test
    void aParenthesisInsideAnAnnotationArgumentStringDoesNotEndIt() {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                final @SuppressWarnings("a)b") int VALUE = 1;
            }
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(LintEngine.applyEdits(unit.source(), result.edits())).contains("@SuppressWarnings(\"a)b\") final int VALUE");
    }

    @Test
    void aDeclarationGroupIsLeftForTheSplitToHappenFirst() {
        // Every declarator of `static private int a = 1, b = 2;` carries the same ModifiersTree, so
        // the rule produced one identical edit per declarator and applyEdits rejected the pair as
        // overlapping. MultipleVariableDeclarations splits the group first and each declaration
        // then gets its own edit. java/lang/reflect/AccessFlag.java was refused over this.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                static private int a = 1, b = 2;
            }
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void theAnnotationTypeKeywordIsNotAnAnnotation() {
        // `@interface` starts with `@` and is not an annotation; hoisting it produced
        // `@interface public`, which does not parse.
        final var unit = JavaParser.parseUnit("""
            @Deprecated
            public @interface Marker {}
            """);
        final var result = new ModifierOrder().apply(LintContext.parseOnly(unit));
        assertThat(LintEngine.applyEdits(unit.source(), result.edits())).contains("public @interface Marker");
    }
}
