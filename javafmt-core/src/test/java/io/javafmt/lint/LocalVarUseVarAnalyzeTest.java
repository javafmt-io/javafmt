package io.javafmt.lint;

import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LocalVarUseVarAnalyzeTest {

    private static final JavafmtConfig ANALYZE = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(""));

    @Test
    void chainInitializerWithMatchingInferredTypeBecomesVar() {
        // List<X> xs = stream.map(...).toList() — analyze sees inferred == declared and rewrites.
        final var source = """
                import java.util.List;
                import java.util.stream.Stream;
                class Fixture {

                    void test() {
                        List<String> xs = Stream.of("a", "b").toList();
                        System.out.println(xs);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
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
                        System.out.println(xs);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted)
                .contains("var xs = Collections.unmodifiableList(input);")
                .contains("List<String> input")
                .doesNotContain("List<String> xs");
    }

    @Test
    void wideningNewBecomesVarAndKeepsItsTypeArguments() {
        final var source = """
                import java.util.ArrayList;
                import java.util.List;
                class Fixture {

                    void test() {
                        List<String> xs = new ArrayList<String>();
                        System.out.println(xs);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("final var xs = new ArrayList<String>();");
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
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("List<String> xs =").doesNotContain("var xs");
    }

    @Test
    void diamondInitializerGainsTheDeclaredTypeArguments() {
        final var source = """
                import java.util.ArrayList;
                class Fixture {

                    void test() {
                        ArrayList<String> xs = new ArrayList<>();
                        xs.add("a");
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("final var xs = new ArrayList<String>();");
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
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
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
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
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
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
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
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
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
                        System.out.println(xs);
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
                        System.out.println(xs);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("var xs = new ArrayList<String>();");
    }

    /** The edits alone, so these tests pin the rewrite and not the renderer's comment placement. */
    private static String rewrittenWithAttribution(final String source) {
        return JavaParser.attributing(
                "",
                session -> {
                    final var attributed = session.attribute(source);
                    final var result = new LocalVarUseVar().apply(LintContext.attributed(attributed));
                    return LintEngine.applyEdits(attributed.unit().source(), result.edits());
                });
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
        assertThat(Javafmt.formatWithResult(source, ANALYZE).output()).doesNotContain("Comparator.<String>comparing");
    }

    private static String ledger(final String body) {
        return """
                import java.util.ArrayList;
                import java.util.Collection;
                import java.util.Comparator;
                import java.util.List;
                import java.util.function.Function;
                import java.util.stream.Stream;

                class Ledger {

                    static class Entry {

                        String label = "entry";
                    }

                    static class Refund extends Entry {

                        String label = "refund";
                    }

                    String describe() {
                        return "invoice";
                    }

                    int count() {
                        return 1;
                    }

                    long size() {
                        return 1L;
                    }

                    List<String> names() {
                        return List.of();
                    }

                    @SuppressWarnings("rawtypes")
                    List untypedNames() {
                        return List.of();
                    }

                    Refund refund() {
                        return new Refund();
                    }

                    static class Box<T> {

                        static <T> Box<T> plain() {
                            return new Box<>();
                        }

                        static <T> SealedBox<T> sealed() {
                            return new SealedBox<>();
                        }

                        static <T> Box<T> of(final List<T> items) {
                            return new Box<>();
                        }

                        @SuppressWarnings("rawtypes")
                        static <T> Box raw() {
                            return new Box<>();
                        }

                        T first() {
                            return null;
                        }
                    }

                    static class SealedBox<T> extends Box<T> {}

                    void show(final Object value) {}

                    void show(final Box<?> box) {}

                    void show(final SealedBox<?> box) {}

                    void show(final CharSequence text) {}

                    void show(final String text) {}

                    void record(final Object value) {}

                    void settle(final boolean flag, final CharSequence other) {
                %s
                    }
                }
                """.formatted(body);
    }

    @ParameterizedTest
    @CsvSource( delimiter = '|', value = { "List<String> xs = Stream.of(\"a\").toList(); record(xs);    | var xs = Stream.of(\"a\").toList();", "CharSequence text = describe(); record(text.length());      | var text = describe();", "String name = describe(); show(name);                       | var name = describe();", "long total = size(); record(total);                         | var total = size();", "List<String> lines = new ArrayList<>(); record(lines);      | var lines = new ArrayList<String>();", "Box<String> box = Box.plain(); show(box);                  | var box = Box.<String>plain();", "List<String> xs = List.<String>of(); record(xs);            | var xs = List.<String>of();", "Box<String> box = Box.<String>sealed(); record(box);       | var box = Box.<String>sealed();", "Box<String> box = Box.sealed(); record(box);               | var box = Box.<String>sealed();", "List<String> lines = new ArrayList<String>(); record(lines.size()); | var lines = new ArrayList<String>();", "Collection<String> all = new ArrayList<String>(); List<Collection<String>> rows = Stream.of(all).toList(); record(rows); | Collection<String> all = new ArrayList<String>(); var rows = Stream.of(all).toList();", "Collection<String> all = names(); List<Collection<String>> rows = Stream.of(all).toList(); record(rows); | Collection<String> all = names(); var rows = Stream.of(all).toList();", "List<Object> xs = List.of(\"a\"); record(xs);               | var xs = List.<Object>of(\"a\");", })
    void aLocalWhoseUsesStayTheSameBecomesVar(final String body, final String expected) {
        assertThat(rewrittenWithAttribution(ledger(body))).contains(expected);
    }

    @ParameterizedTest
    @ValueSource( strings = { "Collection<Object> xs = List.of(\"a\"); record(xs);", "Collection<Integer> ids = new ArrayList<>(List.of(5, 7, 1)); ids.remove(1); record(ids);", "String label = switch (count()) { case 1 -> \"one\"; default -> \"many\"; }; record(label);", "Object label = flag ? \"a\" : \"b\"; show(label);", "List<String> lines = flag ? new ArrayList<>() : new ArrayList<>(); record(lines.get(0).length());", "Box<String> box = Box.sealed(); show(box);", "Box<String> box = Box.<String>sealed(); show(box);", "Box<String> box = Box.<String>raw(); record(box.first().length());", "Box<String> box = Box.<String>of(untypedNames()); record(box.first().length());", "CharSequence text = describe(); show(text);", "Entry entry = refund(); record(entry.label);", "long total = count(); record(total);", "Object value = flag ? \"a\" : 1; record(value);", "CharSequence text = describe(); text = other; record(text);", "String note = null; record(note);", "Runnable task = () -> {}; task.run();", "Function<String, Integer> length = String::length; record(length);", "int[] slots = {1}; record(slots);", "Comparator<String> order = new Comparator<>() { public int compare(String a, String b) { return 0; } }; record(order);", "long cents = 1; record(cents);", "Integer boxed = count(); record(boxed);", })
    void aLocalWhoseUsesWouldChangeKeepsItsType(final String body) {
        assertThat(rewrittenWithAttribution(ledger(body))).contains(body).doesNotContain("var ");
    }

    @Test
    void aLocalOfExactlyTheDeclaredTypeBecomesVarBesideARejectedOne() {
        final var rewritten = rewrittenWithAttribution(ledger("List<String> xs = names(); CharSequence text = describe(); show(text); record(xs);"));
        assertThat(rewritten).contains("var xs = names();").contains("CharSequence text = describe();");
    }

    @Test
    void aWideningNewStaysWithoutAttribution() {
        final var source = ledger("List<String> lines = new ArrayList<String>(); record(lines.size());");
        assertThat(Javafmt.format(source)).contains("final List<String> lines = new ArrayList<>();");
    }

    @ParameterizedTest
    @CsvSource( delimiter = '|', value = { "List<Object> xs = List.<Object>of(\"a\");                   | final var xs = List.<Object>of(\"a\");", "List<Object> xs = List.of(\"a\");                           | final var xs = List.<Object>of(\"a\");", "List<Object> xs = Collections.<Object>singletonList(\"a\"); | final List<Object> xs = Collections.singletonList(\"a\");", })
    void aWitnessTheVarRewriteNeedsIsNeverDropped(final String declaration, final String expected) {
        final var source = """
                import java.util.Collections;
                import java.util.List;

                class Ledger {

                    void settle() {
                        %s
                        System.out.println(xs);
                    }
                }
                """.formatted(declaration);
        assertThat(Javafmt.formatWithResult(source, ANALYZE).output()).contains(expected);
    }
}
