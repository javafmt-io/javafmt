package io.javafmt.printer;

import java.util.List;
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

import io.javafmt.doc.Doc;

/**
 * Group-fitting decisions are the hot path in {@link Printer}; a regression toward
 * O(n²) shows up here long before any user-facing format gets slow. Builds a synthetic
 * doc whose structure forces every nested Group to make its own fit decision in BREAK
 * mode (a method-chain / nested-arglist shape), then measures total print time at
 * N ∈ {100, 1000, 10000} across both {@link PrintStrategy} implementations.
 *
 * <p>Each cell answers a different question:
 * <ul>
 *   <li>{@code greedy} cells: regression in {@code GreedyPrintStrategy.fits} short-circuit /
 *       lookahead behaviour (the canonical O(n²) trap for greedy printers).
 *   <li>{@code wadlerLindig} cells: regression in {@code WadlerLindigPrintStrategy.fitsForward}
 *       memoization or in the deferred-group accounting that lets WL stay linear despite
 *       backtrack-style decisions.
 *   <li>Greedy-vs-WL gap at the same depth: relative cost of the two strategies on a
 *       pathologically nested input — a leading indicator for whether WL needs perf
 *       optimization on real Java code.
 * </ul>
 *
 * <p>Runtime should scale roughly linearly in N for both strategies. Run with
 * {@code gradle :javafmt-core:jmh}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PrinterFitsBenchmark {

    @Param({"100", "1000", "10000"})
    public int depth = 100;

    @Param({"greedy", "wadlerLindig"})
    public String strategy = "greedy";

    private Doc doc = new Doc.Text("");
    private Printer printer = new Printer(1);

    @Setup
    public void setUp() {
        doc = nestedGroups(depth);
        printer = new Printer(1, strategyFor(strategy));
    }

    @Benchmark
    public String printNestedGroups() {
        return printer.print(doc);
    }

    private static PrintStrategy strategyFor(final String name) {
        return switch (name) {
            case "greedy" -> PrintStrategy.greedy();
            case "wadlerLindig" -> PrintStrategy.wadlerLindig();
            default -> throw new IllegalArgumentException("unknown strategy: " + name);
        };
    }

    private static Doc nestedGroups(final int n) {
        var inner = (Doc) new Doc.Text("x");
        for (var i = 0; i < n; i++) {
            inner = new Doc.Group(new Doc.Concat(List.of(
                new Doc.Text("g"),
                new Doc.Line(),
                inner)));
        }
        return inner;
    }
}
