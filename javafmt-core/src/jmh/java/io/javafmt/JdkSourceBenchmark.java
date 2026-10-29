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

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JdkSourceBenchmark {

    @Param({"java/io/BufferedInputStream.java", "java/util/concurrent/ConcurrentHashMap.java"})
    public String file = "java/io/BufferedInputStream.java";

    private String source = "";

    private final JavafmtConfig config = JavafmtConfig.defaults();

    @Setup
    public void setUp() {
        source = RealWorldCorpus.jdk(file);
    }

    @Benchmark
    public void formatSource(final Blackhole blackhole) {
        blackhole.consume(Javafmt.formatWithResult(source, config));
    }
}
