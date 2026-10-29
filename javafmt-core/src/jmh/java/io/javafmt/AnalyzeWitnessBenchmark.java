package io.javafmt;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class AnalyzeWitnessBenchmark {

    private static final JavafmtConfig ANALYZE = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(""));

    @Param({"10", "200",})
    public int witnesses = 10;

    private String source = "";

    @Setup
    public void setUp() {
        source = IntStream.range(0, witnesses)
                .mapToObj(
                        i -> i % 2 == 0
                                ? "    Stream<Integer> drop%d() {\n        return Stream.<Integer>of(%d);\n    }\n".formatted(i, i)
                                : "    void keep%d() {\n        var x = Stream.<Number>of(%d);\n    }\n".formatted(i, i))
                .collect(Collectors.joining("\n", "import java.util.stream.Stream;\n\nclass Witnesses {\n\n", "}\n"));
    }

    @Benchmark
    public FormatResult formatWithAnalysis() {
        return Javafmt.formatWithResult(source, ANALYZE);
    }
}
