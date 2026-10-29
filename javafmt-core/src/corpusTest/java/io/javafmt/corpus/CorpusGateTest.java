package io.javafmt.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.corpus.CorpusRun.FileOutcome;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Formats the JDK's {@code java.base} sources and holds the result against
 * {@code corpus-baseline.properties}. Hand-written fixtures only cover constructs somebody thought
 * to write down; this covers every construct the JDK uses, which is how a renderer that silently
 * drops type parameters, a lint rule that never terminates, or output that stops being idempotent
 * gets noticed the same day it lands.
 *
 * <p>Counts are ceilings. A run that comes in above one fails; a run that comes in below fails too,
 * naming the value to record, so the baseline file stays an accurate account of what is left rather
 * than drifting into fiction.
 */
class CorpusGateTest {

    /**
     * Generous on purpose: this exists to catch a file that never finishes, not to police a
     * millisecond. The whole 1,276-file corpus formats in well under this per file.
     */
    private static final long PER_FILE_BUDGET_MILLIS = 5_000;

    private static final int SLOWEST_REPORTED = 10;

    private static final CorpusBaseline BASELINE = CorpusBaseline.load();

    private static CorpusRun run;

    @BeforeAll
    static void formatTheCorpus() {
        run = CorpusRun.over(sourceZip());
        System.out.println(summary(run));
    }

    @Test
    void theCorpusIsTheOneTheBaselineWasRecordedAgainst() {
        // Every other assertion here is a count, and a count only means something against a known
        // denominator. A different JDK ships different files, and the numbers below would be noise.
        assertThat(run.outcomes()).hasSize(BASELINE.files());
    }

    @Test
    void noMoreFilesFailToParseThanTheBaselineRecords() {
        assertRecorded("unparseable", FileOutcome::unparseable, BASELINE.unparseable());
    }

    @Test
    void noMoreFilesAreRefusedThanTheBaselineRecords() {
        assertRecorded("refused", FileOutcome::refused, BASELINE.refused());
    }

    @Test
    void noMoreFilesFailToReparseThanTheBaselineRecords() {
        // The only entry here that is corruption rather than a shortfall: javafmt wrote the file
        // and javac cannot read what it wrote. It is recorded the same way as the rest only so the
        // gate could be introduced against the formatter as it is; it belongs at zero.
        assertRecorded("notReparsing", o -> o.formatted() && !o.reparses(), BASELINE.notReparsing());
    }

    @Test
    void noMoreFilesAreNonIdempotentThanTheBaselineRecords() {
        assertRecorded("notIdempotent", o -> o.formatted() && !o.idempotent(), BASELINE.notIdempotent());
    }

    @Test
    void noMoreFilesExceedTheLineWidthThanTheBaselineRecords() {
        assertRecorded("overWidth", o -> o.formatted() && o.overWidthLines() > 0, BASELINE.overWidth());
    }

    @Test
    void everyFileFormatsWithinTheBudget() {
        assertThat(run.slowest())
                .hasValueSatisfying(
                        slowest -> assertThat(slowest.millis())
                                .describedAs("%s took the longest to format", slowest.name())
                                .isLessThanOrEqualTo(PER_FILE_BUDGET_MILLIS));
    }

    private static void assertRecorded(final String key, final Predicate<FileOutcome> property, final int recorded) {
        final var offenders = run.names(property);
        assertThat(offenders.size())
                .describedAs(
                        "%s: %d files, baseline records %d. Set %s=%d in corpus-baseline.properties. First few: %s",
                        key,
                        offenders.size(),
                        recorded,
                        key,
                        offenders.size(),
                        offenders.stream().limit(5).toList())
                .isEqualTo(recorded);
    }

    private static Path sourceZip() {
        final var configured = System.getProperty("javafmt.corpus.srczip");
        assertThat(configured)
                .describedAs("javafmt.corpus.srczip is unset; run this suite through gradle :javafmt-core:corpusTest")
                .isNotNull();
        final var path = Path.of(configured);
        assertThat(path)
                .describedAs("the toolchain at %s ships no lib/src.zip, so there is no corpus to format", path)
                .exists();
        return path;
    }

    private static String summary(final CorpusRun corpus) {
        return """

                javafmt corpus gate — %d files from java.base/java/{io,lang,nio,time,util}
                  parse errors            %5d   baseline %d
                  refused (internal)      %5d   baseline %d
                  output does not reparse %5d   baseline %d
                  not idempotent          %5d   baseline %d
                  files over 150 chars    %5d   baseline %d
                  over-width lines        %5d
                  total                   %5d ms
                  slowest files (budget %d ms each)
                %s""".formatted(
                corpus.outcomes().size(),
                corpus.count(FileOutcome::unparseable),
                BASELINE.unparseable(),
                corpus.count(FileOutcome::refused),
                BASELINE.refused(),
                corpus.count(o -> o.formatted() && !o.reparses()),
                BASELINE.notReparsing(),
                corpus.count(o -> o.formatted() && !o.idempotent()),
                BASELINE.notIdempotent(),
                corpus.count(o -> o.formatted() && o.overWidthLines() > 0),
                BASELINE.overWidth(),
                corpus.outcomes().stream().mapToLong(FileOutcome::overWidthLines).sum(),
                corpus.totalMillis(),
                PER_FILE_BUDGET_MILLIS,
                corpus.slowest(SLOWEST_REPORTED)
                        .stream()
                        .map(o -> "    %5d ms  %s%n".formatted(o.millis(), o.name()))
                        .collect(Collectors.joining()));
    }
}
