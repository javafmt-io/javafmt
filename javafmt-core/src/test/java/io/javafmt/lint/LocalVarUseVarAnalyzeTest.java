package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Javafmt;
import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

class LocalVarUseVarAnalyzeTest {

    @Test
    void chainInitializerWithMatchingInferredTypeBecomesVar() {
        // List<X> xs = stream.map(...).toList() — analyze sees inferred == declared and rewrites.
        final var source = """
                import java.util.List;
                import java.util.stream.Stream;
                class Fixture {

                    void test() {
                        List<String> xs = Stream.of("a", "b").toList();
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("var xs = Stream.of(\"a\", \"b\").toList();")
                .doesNotContain("List<String> xs");
    }

    @Test
    void factoryWithDifferentReceiverRewrites() {
        // The narrow same-receiver pattern doesn't fire (receiver Collections != List), so the
        // analyze path is the only thing that can rewrite this.
        final var source = """
                import java.util.Collections;
                import java.util.List;
                class Fixture {

                    void test(final List<String> input) {
                        List<String> xs = Collections.unmodifiableList(input);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("var xs = Collections.unmodifiableList(input);")
                .contains("List<String> input")
                .doesNotContain("List<String> xs");
    }

    @Test
    void wideningDeclarationKeepsItsDeclaredType() {
        // List<X> xs = new ArrayList<X>() — declared type is wider than inferred. Rewriting to
        // `var xs` silently narrows the static type the author chose, and breaks outright as soon
        // as the local is reassigned from anything that is a List but not an ArrayList. The
        // declared type stays; RedundantTypeArguments is free to diamond-ify the constructor.
        final var source = """
                import java.util.ArrayList;
                import java.util.List;
                class Fixture {

                    void test() {
                        List<String> xs = new ArrayList<String>();
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("final List<String> xs = new ArrayList<>();")
                .doesNotContain("var xs");
    }

    @Test
    void reassignmentAfterWideningStillCompiles() {
        final var source = """
                import java.util.ArrayList;
                import java.util.List;
                class Fixture {

                    void test(final List<String> other) {
                        List<String> xs = new ArrayList<String>();
                        xs = other;
                        System.out.println(xs);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted).contains("List<String> xs =").doesNotContain("var xs");
    }

    @Test
    void diamondInitializerIsNotRewritten() {
        // `var xs = new ArrayList<>()` has no target type left for the diamond to infer from.
        final var source = """
                import java.util.ArrayList;
                class Fixture {

                    void test() {
                        ArrayList<String> xs = new ArrayList<>();
                        xs.add("a");
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("ArrayList<String> xs = new ArrayList<>();")
                .doesNotContain("var xs");
    }

    @Test
    void lambdaInitializerIsNotRewritten() {
        // A lambda has no type of its own; `var s = () -> "x"` does not compile.
        final var source = """
                import java.util.function.Supplier;
                class Fixture {

                    void test() {
                        Supplier<String> s = () -> "x";
                        s.get();
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("Supplier<String> s = () -> \"x\";")
                .doesNotContain("var s");
    }

    @Test
    void methodReferenceInitializerIsNotRewritten() {
        final var source = """
                import java.util.function.Supplier;
                class Fixture {

                    void test() {
                        Supplier<String> s = Fixture::make;
                        s.get();
                    }

                    static String make() {
                        return "x";
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("Supplier<String> s = Fixture::make;")
                .doesNotContain("var s");
    }

    @Test
    void bareArrayInitializerIsNotRewritten() {
        // `var xs = {1, 2}` does not compile — an array initializer needs the declared type.
        final var source = """
                class Fixture {

                    void test() {
                        int[] xs = {1, 2};
                        System.out.println(xs.length);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted).contains("int[] xs = {1, 2,};").doesNotContain("var xs");
    }

    @Test
    void boxedDeclarationWithPrimitiveInitializerIsNotRewritten() {
        // `var i = 5` types i as int, so `i.hashCode()` stops compiling and `i = null` with it.
        final var source = """
                class Fixture {

                    void test() {
                        Integer i = 5;
                        System.out.println(i.hashCode());
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted).contains("Integer i = 5;").doesNotContain("var i");
    }

    @Test
    void chainIsLeftAloneWithoutAttribution() {
        // Same chain as above but via the parse-only entry point — the analyze path mustn't
        // fire because there's no attribution to verify the type match.
        final var source = """
                import java.util.List;
                import java.util.stream.Stream;
                class Fixture {

                    void test() {
                        List<String> xs = Stream.of("a", "b").toList();
                    }
                }
                """;
        final var formatted = Javafmt.format(source);
        assertThat(formatted).contains("List<String> xs");
    }

    @Test
    void existingNarrowNewMatchStillFiresUnderAnalyze() {
        // ArrayList<X> x = new ArrayList<X>() — the narrow new-match pattern should still
        // rewrite to var even when --analyze is on.
        final var source = """
                import java.util.ArrayList;
                class Fixture {

                    void test() {
                        ArrayList<String> xs = new ArrayList<String>();
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted).contains("var xs = new ArrayList<String>();");
    }

    /** The edits alone, so these tests pin the rewrite and not the renderer's comment placement. */
    private static String rewrittenWithAttribution(final String source) {
        final var attributed = JavaParser.parseAndAttribute(source, "");
        final var result = new LocalVarUseVar().apply(LintContext.attributed(attributed));
        return LintEngine.applyEdits(attributed.unit().source(), result.edits());
    }

    @Test
    void staticFactoryWithMatchingReceiverGetsTypeWitness() {
        // Optional<Path> x = Optional.empty() must rewrite to `var x = Optional.<Path>empty()`.
        // A naive `var` rewrite would infer Optional<Object> and change the type, so the rule
        // lifts the variable's type arguments into a type witness on the call.
        final var source = """
                import java.nio.file.Path;
                import java.util.Optional;
                class Fixture {

                    void test() {
                        Optional<Path> cacheFile = Optional.empty();
                        System.out.println(cacheFile);
                    }
                }
                """;
        assertThat(rewrittenWithAttribution(source)).contains("var cacheFile = Optional.<Path>empty();");
    }

    @Test
    void typeWitnessGoesAfterTheDotWithoutTouchingAnInterveningComment() {
        final var source = """
                import java.util.Optional;
                class Fixture {

                    void test() {
                        Optional<String> o = Optional /* a.b */.empty();
                        System.out.println(o);
                    }
                }
                """;
        assertThat(rewrittenWithAttribution(source)).contains("var o = Optional /* a.b */.<String>empty();");
    }

    @Test
    void factoryWithMoreTypeParametersThanTheDeclaredTypeIsLeftAlone() {
        // Comparator.comparing declares two type parameters where Comparator<String> supplies one.
        // Lifting the declared argument onto it produced `Comparator.<String>comparing(...)`, which
        // javac rejects with "wrong number of type arguments; required 2".
        final var source = """
                import java.util.Comparator;
                class Fixture {

                    void test() {
                        Comparator<String> c = Comparator.comparing(String::length);
                        System.out.println(c);
                    }
                }
                """;
        assertThat(Javafmt.formatWithAnalyze(source, "").output()).doesNotContain("Comparator.<String>comparing");
    }
}
