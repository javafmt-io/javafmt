package io.javafmt.parser;

import io.javafmt.BenchmarkCorpus;
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

import java.util.concurrent.TimeUnit;

/**
 * Isolation benchmark for {@link JavaParser#parseUnit}. Companion to
 * {@code JavafmtFormatBenchmark}: this measures only the parse phase on the same
 * corpus sources.
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
        source = BenchmarkCorpus.named(corpus);
    }

    @Benchmark
    public void parseSource(final Blackhole blackhole) {
        blackhole.consume(JavaParser.parseUnit(source));
    }
}
