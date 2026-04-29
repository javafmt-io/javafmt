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

import io.javafmt.printer.PrintStrategy;

/**
 * End-to-end {@link Javafmt#format} timing on real-shaped Java sources, parameterized
 * over both {@link PrintStrategy} implementations. {@link io.javafmt.printer.PrinterFitsBenchmark}
 * answers the algorithmic question (does Group-fit decision time stay linear?); this
 * benchmark answers the user-facing question (how fast is the formatter on inputs
 * people actually format?).
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
 *   <li>Greedy-vs-WL gap on the same source: real-world cost of the WL strategy versus
 *       Greedy. Drives the phase-6 memoization decision: if WL is meaningfully slower
 *       on these inputs, it's worth adding {@code fitsForward} memoization; if the gap
 *       is small, memoization stays deferred.
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

    @Param({"methodChains", "wideRecords", "nestedConditionals"})
    public String corpus = "methodChains";

    @Param({"greedy", "wadlerLindig"})
    public String strategy = "greedy";

    private String source = "";
    private PrintStrategy printStrategy = PrintStrategy.greedy();
    private JavafmtConfig config = JavafmtConfig.defaults();

    @Setup
    public void setUp() {
        source = sourceFor(corpus);
        printStrategy = strategyFor(strategy);
        config = JavafmtConfig.defaults();
    }

    @Benchmark
    public void formatSource(final Blackhole blackhole) {
        blackhole.consume(Javafmt.formatWithResult(source, config, printStrategy));
    }

    private static String sourceFor(final String name) {
        return switch (name) {
            case "methodChains" -> BenchmarkCorpus.METHOD_CHAINS;
            case "wideRecords" -> BenchmarkCorpus.WIDE_RECORDS;
            case "nestedConditionals" -> BenchmarkCorpus.NESTED_CONDITIONALS;
            default -> throw new IllegalArgumentException("unknown corpus: " + name);
        };
    }

    private static PrintStrategy strategyFor(final String name) {
        return switch (name) {
            case "greedy" -> PrintStrategy.greedy();
            case "wadlerLindig" -> PrintStrategy.wadlerLindig();
            default -> throw new IllegalArgumentException("unknown strategy: " + name);
        };
    }
}
