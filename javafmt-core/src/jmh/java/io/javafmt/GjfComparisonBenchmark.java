package io.javafmt;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Head-to-head against google-java-format on identical input, so "fast" is a comparison rather
 * than a claim.
 *
 * <p>Two things keep the comparison honest:
 *
 * <ul>
 *   <li><b>Shared corpus.</b> google-java-format rejects syntax it does not yet support, and this
 *       corpus is Java 25. Setup drops any file either formatter cannot handle and prints how many
 *       were excluded, so a shrinking corpus is visible rather than silently flattering whichever
 *       tool parsed more.
 *   <li><b>Different jobs.</b> javafmt's number includes its lint pass — inserting {@code final},
 *       adding braces, removing unused imports — which google-java-format has no equivalent of and
 *       which costs a re-parse per rewritten file. This measures what each tool does end to end on
 *       one file, not a like-for-like layout-only comparison. {@link BatchFormatBenchmark} is the
 *       right place to look for javafmt's batch throughput.
 * </ul>
 *
 * <p>Run with {@code gradle :javafmt-core:jmh -PjmhInclude=GjfComparisonBenchmark.*}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class GjfComparisonBenchmark {

    private static final int CANDIDATES = 32;

    private List<String> shared = List.of();

    private JavafmtConfig config = JavafmtConfig.defaults();

    private JavafmtConfig layoutOnly = JavafmtConfig.defaults();

    private Formatter gjf = new Formatter();

    @Setup
    public void setUp() {
        gjf = new Formatter();
        config = JavafmtConfig.defaults();
        layoutOnly = JavafmtConfig.defaults().withLint(false);
        final var candidates = RealWorldCorpus.clean(CANDIDATES);
        shared = candidates.stream()
                .filter(GjfComparisonBenchmark::bothFormattersAccept)
                .toList();
        System.out.printf(
                "%n[corpus] %d of %d files formatted by both tools; %d excluded (google-java-format could not parse them)%n",
                shared.size(),
                candidates.size(),
                candidates.size() - shared.size());
        if (shared.isEmpty()) {
            throw new IllegalStateException("no file is accepted by both formatters; the comparison would be vacuous");
        }
    }

    private static boolean bothFormattersAccept(final String source) {
        try {
            new Formatter().formatSource(source);
        } catch (FormatterException | RuntimeException e) {
            return false;
        }
        return !Javafmt.formatWithResult(source).hasErrors();
    }

    @Benchmark
    public void javafmt(final Blackhole blackhole) {
        shared.forEach(source -> blackhole.consume(Javafmt.format(source, config)));
    }

    /** Layout only, which is the like-for-like comparison against a formatter that does not lint. */
    @Benchmark
    public void javafmtWithoutLint(final Blackhole blackhole) {
        shared.forEach(source -> blackhole.consume(Javafmt.format(source, layoutOnly)));
    }

    @Benchmark
    public void googleJavaFormat(final Blackhole blackhole) throws FormatterException {
        for (final var source : shared) {
            blackhole.consume(gjf.formatSource(source));
        }
    }
}
