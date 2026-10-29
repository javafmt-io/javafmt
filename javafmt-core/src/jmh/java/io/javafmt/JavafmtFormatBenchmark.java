package io.javafmt;

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

/**
 * End-to-end {@link Javafmt#format} timing on real-shaped Java sources.
 * {@link io.javafmt.printer.PrinterFitsBenchmark} answers the algorithmic question (does
 * Group-fit decision time stay linear?); this benchmark answers the user-facing question
 * (how fast is the formatter on inputs people actually format?).
 *
 * <p>Each cell answers a different question:
 * <ul>
 *   <li>{@code methodChains}: cost of long fluent chains where each {@code .method(...)}
 *       call adds another Group / Line decision. A regression here suggests printer
 *       overhead in the chain-breaking path or in {@code DocBuilder} construction
 *       of chained-call IR.
 *   <li>{@code wideRecords}: cost of formatting many wide record component lists. A
 *       regression here suggests the per-component break decision or comma-separator
 *       rendering got slower.
 *   <li>{@code nestedConditionals}: cost of deeply nested ternary / if-else chains. A
 *       regression here suggests the printer's lookahead behaviour on deep nesting
 *       degraded, or that DocBuilder generates more groups for these shapes than before.
 * </ul>
 *
 * <p>Run with {@code gradle :javafmt-core:jmh}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JavafmtFormatBenchmark {

    @Param({"methodChains", "wideRecords", "nestedConditionals", "deepNesting", "deepTwoLinkNesting"})
    public String corpus = "methodChains";

    private String source = "";

    private JavafmtConfig config = JavafmtConfig.defaults();

    @Setup
    public void setUp() {
        source = sourceFor(corpus);
        config = JavafmtConfig.defaults();
    }

    @Benchmark
    public void formatSource(final Blackhole blackhole) {
        blackhole.consume(Javafmt.formatWithResult(source, config));
    }

    private static String sourceFor(final String name) {
        return switch (name) {
            case "methodChains" -> BenchmarkCorpus.METHOD_CHAINS;
            case "wideRecords" -> BenchmarkCorpus.WIDE_RECORDS;
            case "nestedConditionals" -> BenchmarkCorpus.NESTED_CONDITIONALS;
            case "deepNesting" -> BenchmarkCorpus.DEEP_NESTING;
            case "deepTwoLinkNesting" -> BenchmarkCorpus.DEEP_TWO_LINK_NESTING;
            default -> throw new IllegalArgumentException("unknown corpus: " + name);
        };
    }
}
