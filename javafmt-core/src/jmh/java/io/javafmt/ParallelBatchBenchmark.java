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

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ParallelBatchBenchmark {

    @Param({"64", "256",})
    public int files = 64;

    @Param({"1", "4",})
    public int threads = 1;

    @Param({"clean", "dirty", "mixed",})
    public String variant = "clean";

    private List<String> sources = List.of();

    private JavafmtConfig config = JavafmtConfig.defaults();

    @Setup
    public void setUp() {
        sources = switch (variant) {
            case "dirty" -> cycled(RealWorldCorpus.dirty(Integer.MAX_VALUE));
            case "mixed" -> {
                final var large = RealWorldCorpus.jdk("java/util/concurrent/ConcurrentHashMap.java");
                final var clean = cycled(RealWorldCorpus.clean(Integer.MAX_VALUE));
                yield IntStream.range(0, files)
                        .mapToObj(i -> i < files / 16 ? large : clean.get(i))
                        .toList();
            }
            default -> cycled(RealWorldCorpus.clean(Integer.MAX_VALUE));
        };
        config = JavafmtConfig.defaults();
    }

    private List<String> cycled(final List<String> corpus) {
        return IntStream.range(0, files)
                .mapToObj(i -> corpus.get(i % corpus.size()))
                .toList();
    }

    @Benchmark
    public List<FormatResult> formatAll() {
        return Javafmt.formatAll(sources, config, threads);
    }
}
