package io.javafmt;

import java.util.List;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.infra.Blackhole;

/**
 * What batching buys, measured rather than asserted.
 *
 * <p>Every cell formats the same {@value #FILES} real source files; only the number of files handed
 * to {@link Javafmt#formatAll} at a time changes. {@code batchSize=1} is the old per-file pipeline,
 * so the ratio between it and {@code batchSize=64} is the win from sharing javac task setup.
 *
 * <p>The {@code dirty} axis is the one that matters. A clean file is parsed once; a file the lint
 * pass rewrites is re-parsed once per round, and before batching each of those re-parses paid full
 * task setup. Since {@code FinalLocalVariable} and {@code NeedBraces} fire on almost any
 * unformatted input, {@code dirty} is what a first run over a codebase actually costs.
 *
 * <p>Run with {@code gradle :javafmt-core:jmh -PjmhInclude=BatchFormatBenchmark.*}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BatchFormatBenchmark {

    /** Held constant across cells so results are comparable per file, not per invocation. */
    private static final int FILES = 64;

    @Param({"1", "8", "64",})
    public int batchSize = 1;

    @Param({"clean", "dirty",})
    public String variant = "clean";

    private List<List<String>> batches = List.of();

    private JavafmtConfig config = JavafmtConfig.defaults();

    @Setup
    public void setUp() {
        final var sources = "dirty".equals(variant) ? RealWorldCorpus.dirty(FILES) : RealWorldCorpus.clean(FILES);
        batches = IntStream.iterate(0, start -> start < sources.size(), start -> start + batchSize)
                .mapToObj(start -> sources.subList(start, Math.min(sources.size(), start + batchSize)))
                .toList();
        config = JavafmtConfig.defaults();
    }

    /** Single-threaded on purpose: this measures task-setup amortization, not render parallelism. */
    @Benchmark
    public void formatCorpus(final Blackhole blackhole) {
        batches.forEach(batch -> blackhole.consume(Javafmt.formatAll(batch, config, 1)));
    }
}
