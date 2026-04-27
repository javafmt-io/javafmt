package io.github.jschneidereit.grind.parser;

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

import io.github.jschneidereit.grind.BenchmarkCorpus;

/**
 * Isolation benchmark for {@link JavaParser#parseUnit}. Companion to
 * {@code GrindFormatBenchmark}: this measures only the parse phase on the same
 * corpus sources, no print strategy axis.
 *
 * <p>The CPU profile of {@code GrindFormatBenchmark} pinned ~91% of benchmark-thread
 * cycles inside javac task initialization (boot classpath scan, JarFile inflate,
 * ServiceLoader walk). If parse-only time tracks within a few percent of full-format
 * time, the profile is correct and reusing the file manager / skipping processor
 * init is the right next step. If parse-only is much smaller than full-format,
 * the profile is misleading and the optimization plan needs a rethink.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ParseOnlyBenchmark {

    @Param({"methodChains", "wideRecords", "nestedConditionals"})
    public String corpus = "methodChains";

    private String source = "";

    @Setup
    public void setUp() {
        source = sourceFor(corpus);
    }

    @Benchmark
    public void parseSource(final Blackhole blackhole) {
        blackhole.consume(JavaParser.parseUnit(source));
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
