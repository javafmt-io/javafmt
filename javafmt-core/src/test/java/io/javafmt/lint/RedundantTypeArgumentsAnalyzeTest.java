package io.javafmt.lint;

import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedundantTypeArgumentsAnalyzeTest {
    private static final JavafmtConfig ANALYZE = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(""));

    @Test
    void runningOnAnAttributionWhoseSessionHasEndedFailsInsteadOfSilentlySkipping() {
        final var source = """
                import java.util.Optional;
                class Fixture {

                    Optional<String> test() {
                        return Optional.<String>of("hi");
                    }
                }
                """;
        final var attributed = JavaParser.attributing("", session -> session.attribute(source));
        assertThatThrownBy(() -> new RedundantTypeArguments().apply(LintContext.attributed(attributed))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void streamOfRedundantWitnessOverConcreteArgumentsIsDropped() {
        final var source = """
                import java.util.stream.Stream;
                class Fixture {

                    Stream<Integer> test() {
                        return Stream.<Integer>of(1, 2, 3);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("Stream.of(1, 2, 3);").doesNotContain("<Integer>of");
    }

    @Test
    void aWitnessNamingATypeFromAnotherFileOfTheBatchIsDropped() {
        final var query = """
                package p;

                import java.util.List;

                class Query {
                    List<Declared> all() {
                        return List.<Declared>of(new Declared("a"));
                    }
                }
                """;
        final var declared = """
                package p;

                record Declared(String text) {}
                """;
        final var results = Javafmt.formatAll(List.of(query, declared), ANALYZE, 1);
        assertThat(results.getFirst().output()).contains("return List.of(new Declared(\"a\"));");
    }

    @Test
    void aWitnessHoldingACommentIsKept() {
        final var source = """
                import java.util.stream.Stream;
                class Fixture {

                    Stream<Integer> test() {
                        return Stream.</* boxed */ Integer>of(1, 2, 3);
                    }
                }
                """;
        final var result = Javafmt.formatWithResult(source, ANALYZE.withVerifyOutput(true));
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.output()).contains("Stream.</* boxed */ Integer>of(1, 2, 3);");
    }

    @Test
    void optionalOfRedundantWitnessIsDropped() {
        final var source = """
                import java.util.Optional;
                class Fixture {

                    Optional<String> test() {
                        return Optional.<String>of("hi");
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("Optional.of(\"hi\")").doesNotContain("<String>of");
    }

    @Test
    void widerWitnessOverNarrowerArgumentsIsKept() {
        // No target type (var receiver), so dropping <Number> lets javac infer Stream<Integer>
        // from the int literals — different type, must keep witness.
        final var source = """
                import java.util.stream.Stream;
                class Fixture {

                    void test() {
                        var x = Stream.<Number>of(1, 2);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("Stream.<Number>of(1, 2)");
    }

    @Test
    void emptyArgumentsWitnessIsKept() {
        // No target type and no arguments — javac would infer Stream<Object> without the witness.
        final var source = """
                import java.util.stream.Stream;
                class Fixture {

                    void test() {
                        var x = Stream.<Integer>of();
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("Stream.<Integer>of()");
    }

    @Test
    void multipleCandidatesEachDecidedIndependently() {
        final var source = """
                import java.util.stream.Stream;
                class Fixture {

                    void test() {
                        var dropMe = Stream.<Integer>of(1, 2);
                        var keepMe = Stream.<Number>of(1, 2);
                        System.out.println(dropMe.count() + keepMe.count());
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("var dropMe = Stream.of(1, 2);").contains("var keepMe = Stream.<Number>of(1, 2);");
    }

    @Test
    void multiTypeArgumentWitnessIsDropped() {
        // Witness spans `<String, Integer>` — internal comma must not split the strip range.
        final var source = """
                import java.util.Map;
                class Fixture {

                    Map<String, Integer> test() {
                        return Map.<String, Integer>of("a", 1);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("Map.of(\"a\", 1)").doesNotContain("<String, Integer>of");
    }

    @Test
    void manyCandidatesMixedVerdicts() {
        // Five witnesses, three droppable + two keepable, interleaved. Drives the batched
        // re-attribution past the two-candidate baseline and confirms each call gets its own
        // verdict from the single shared re-attribution.
        final var source = """
                import java.util.stream.Stream;
                class Fixture {

                    Stream<Integer> a() { return Stream.<Integer>of(1, 2); }

                    void b() { var x = Stream.<Number>of(1, 2); System.out.println(x); }

                    Stream<Integer> c() { return Stream.<Integer>of(3, 4); }

                    void d() { var y = Stream.<Number>of(3, 4); System.out.println(y); }

                    Stream<Integer> e() { return Stream.<Integer>of(5); }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted)
                .contains("return Stream.of(1, 2);")
                .contains("return Stream.of(3, 4);")
                .contains("return Stream.of(5);")
                .contains("var x = Stream.<Number>of(1, 2);")
                .contains("var y = Stream.<Number>of(3, 4);");
    }

    @Test
    void fastPathStillFiresUnderAnalyze() {
        // Constructor diamond rewrite must still apply when there is a classpath.
        final var source = """
                import java.util.ArrayList;
                import java.util.List;
                class Fixture {

                    List<String> field = new ArrayList<String>();
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("new ArrayList<>();");
    }

    @Test
    void redundantWitnessOnAnInnerChainLinkIsDropped() {
        // `Stream.<String>of("a")` and the enclosing `.count()` link report the same start offset,
        // so verification has to identify the call by its method name, not by where it starts.
        // Matching on the start offset finds the outer link and compares `long` against
        // `Stream<String>`, which never matches and leaves the redundant witness in place.
        final var source = """
                import java.util.stream.Stream;

                class Fixture {

                    void test() {
                        final long n = Stream.<String>of("a").count();
                        System.out.println(n);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("Stream.of(\"a\").count()").doesNotContain("<String>of");
    }

    @Test
    void witnessOnAnInnerChainLinkThatMattersIsKept() {
        final var source = """
                import java.util.stream.Stream;

                class Fixture {

                    void test() {
                        final long n = Stream.<Object>of("a").count();
                        System.out.println(n);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("Stream.<Object>of(\"a\")");
    }

    @Test
    void witnessCarryingATypeAnnotationIsKept() {
        final var source = """
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;
                import java.util.stream.Stream;

                class Fixture {

                    @Target(ElementType.TYPE_USE)
                    @interface Maybe {}

                    void test() {
                        final Stream<String> s = Stream.<@Maybe String>of("a");
                        System.out.println(s);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithResult(source, ANALYZE).output();
        assertThat(formatted).contains("Stream.<@Maybe String>of(\"a\")");
    }
}
