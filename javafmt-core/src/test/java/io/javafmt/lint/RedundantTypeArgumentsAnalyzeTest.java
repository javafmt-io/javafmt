package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Javafmt;
import org.junit.jupiter.api.Test;

class RedundantTypeArgumentsAnalyzeTest {

    @Test
    void streamOfRedundantWitnessOverConcreteArgsIsDropped() {
        final var source = """
                import java.util.stream.Stream;
                class Fixture {

                    Stream<Integer> test() {
                        return Stream.<Integer>of(1, 2, 3);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("Stream.of(1, 2, 3);")
                .doesNotContain("<Integer>of");
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
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("Optional.of(\"hi\")")
                .doesNotContain("<String>of");
    }

    @Test
    void widerWitnessOverNarrowerArgsIsKept() {
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
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted).contains("Stream.<Number>of(1, 2)");
    }

    @Test
    void emptyArgsWitnessIsKept() {
        // No target type and no args — javac would infer Stream<Object> without the witness.
        final var source = """
                import java.util.stream.Stream;
                class Fixture {

                    void test() {
                        var x = Stream.<Integer>of();
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
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
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("var dropMe = Stream.of(1, 2);")
                .contains("var keepMe = Stream.<Number>of(1, 2);");
    }

    @Test
    void multiTypeArgWitnessIsDropped() {
        // Witness spans `<String, Integer>` — internal comma must not split the strip range.
        final var source = """
                import java.util.Map;
                class Fixture {

                    Map<String, Integer> test() {
                        return Map.<String, Integer>of("a", 1);
                    }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("Map.of(\"a\", 1)")
                .doesNotContain("<String, Integer>of");
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

                    void b() { var x = Stream.<Number>of(1, 2); }

                    Stream<Integer> c() { return Stream.<Integer>of(3, 4); }

                    void d() { var y = Stream.<Number>of(3, 4); }

                    Stream<Integer> e() { return Stream.<Integer>of(5); }
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("return Stream.of(1, 2);")
                .contains("return Stream.of(3, 4);")
                .contains("return Stream.of(5);")
                .contains("var x = Stream.<Number>of(1, 2);")
                .contains("var y = Stream.<Number>of(3, 4);");
    }

    @Test
    void fastPathStillFiresUnderAnalyze() {
        // Constructor diamond rewrite must still apply when --analyze is on.
        final var source = """
                import java.util.ArrayList;
                import java.util.List;
                class Fixture {

                    List<String> field = new ArrayList<String>();
                }
                """;
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
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
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted)
                .contains("Stream.of(\"a\").count()")
                .doesNotContain("<String>of");
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
        final var formatted = Javafmt.formatWithAnalyze(source, "").output();
        assertThat(formatted).contains("Stream.<Object>of(\"a\")");
    }
}
