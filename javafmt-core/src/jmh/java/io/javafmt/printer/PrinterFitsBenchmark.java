package io.javafmt.printer;

import io.javafmt.doc.Doc;
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

/**
 * Group-fitting decisions are the hot path in {@link Printer}; a regression toward
 * O(n²) shows up here long before any user-facing format gets slow. Builds a synthetic
 * doc whose structure forces every nested Group to make its own fit decision in BREAK
 * mode (a method-chain / nested-arglist shape), then measures total print time at
 * N ∈ {100, 1000, 10000}. A regression here points at {@code PrintEngine.fitsForward}'s
 * short-circuit.
 *
 * <p>Runtime should scale roughly linearly in N. Run with {@code gradle :javafmt-core:jmh}.
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

    private Doc doc = new Doc.Text("");

    private final Printer printer = new Printer(1);

    @Setup
    public void setUp() {
        doc = nestedGroups(depth);
    }

    @Benchmark
    public String printNestedGroups() {
        return printer.print(doc);
    }

    private static Doc nestedGroups(final int n) {
        var inner = (Doc) new Doc.Text("x");
        for (var i = 0; i < n; i++) {
            inner = new Doc.Group(new Doc.Concat(List.of(new Doc.Text("g"), Doc.LINE, inner)));
        }
        return inner;
    }
}
