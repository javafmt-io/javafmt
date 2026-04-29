package io.javafmt.builder;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import io.javafmt.BenchmarkCorpus;
import io.javafmt.JavafmtConfig;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParsedUnit;

/**
 * Isolation benchmark for {@link DocBuilder#buildWithFallbacks}. Sister benchmark to
 * {@code ParseOnlyBenchmark}: parsing happens once in {@code @Setup} so each measured
 * invocation only times the AST → Doc IR walk and comment-attachment work that
 * {@code DocBuilder} performs.
 *
 * <p>After Fix 2 collapsed parse cost from ~950 µs to ~30 µs per call, {@code DocBuilder}
 * became the largest single piece of *our* code in the {@code JavafmtFormatBenchmark}
 * profile (20–33% of bench-thread CPU per cell). This benchmark answers two questions
 * the end-to-end benchmark can't:
 *
 * <ul>
 *   <li>What's the absolute per-call cost of {@code DocBuilder} on each corpus shape?
 *       Use this to size any optimization effort against the printer (~5 µs) and the
 *       residual javac scanner+parser (~30 µs).
 *   <li>Does a regression in {@code DocBuilder}'s tree-walking, comment-attachment, or
 *       fallback machinery show up here? End-to-end timing dilutes those changes; this
 *       benchmark surfaces them directly.
 * </ul>
 *
 * <p>No print-strategy axis: {@code DocBuilder} is strategy-agnostic. Run with
 * {@code gradle :javafmt-core:jmh}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DocBuilderBenchmark {

    @Param({"methodChains", "wideRecords", "nestedConditionals"})
    public String corpus = "methodChains";

    private ParsedUnit unit = parse(BenchmarkCorpus.METHOD_CHAINS);
    private JavafmtConfig config = JavafmtConfig.defaults();

    @Setup
    public void setUp() {
        unit = parse(sourceFor(corpus));
        config = JavafmtConfig.defaults();
    }

    @Benchmark
    public void buildDoc(final Blackhole blackhole) {
        blackhole.consume(DocBuilder.buildWithFallbacks(unit, config));
    }

    private static ParsedUnit parse(final String source) {
        return JavaParser.parseUnit(source);
    }

    private static String sourceFor(final String name) {
        return switch (name) {
            case "methodChains" -> BenchmarkCorpus.METHOD_CHAINS;
            case "wideRecords" -> BenchmarkCorpus.WIDE_RECORDS;
            case "nestedConditionals" -> BenchmarkCorpus.NESTED_CONDITIONALS;
            default -> throw new IllegalArgumentException("unknown corpus: " + name);
        };
    }
}
