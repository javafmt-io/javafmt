package io.javafmt.corpus;

import io.javafmt.Diagnostic;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.parser.JavaParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * One pass of javafmt over the whole corpus, kept as a value so every property the gate checks is
 * measured from the same run rather than reformatting 1,300 files once per assertion.
 */
record CorpusRun(List<FileOutcome> outcomes, long totalMillis) {

    private static final int LINE_WIDTH = 150;

    static CorpusRun over(final Path srcZip) {
        final var started = System.nanoTime();
        final var outcomes = JdkCorpus.load(srcZip).stream().map(CorpusRun::formatOne).toList();
        return new CorpusRun(outcomes, (System.nanoTime() - started) / 1_000_000);
    }

    private static FileOutcome formatOne(final JdkCorpus.Entry entry) {
        final var config = JavafmtConfig.defaults();
        final var started = System.nanoTime();
        final var result = Javafmt.formatWithResult(entry.source(), config);
        final var millis = (System.nanoTime() - started) / 1_000_000;
        final var unparseable = result.diagnostics().stream().anyMatch(d -> d instanceof Diagnostic.ParseError);
        final var refused = result.diagnostics()
                .stream()
                .anyMatch(d -> d instanceof Diagnostic.InternalError);
        if (unparseable || refused) {
            return new FileOutcome(entry.name(), unparseable, refused, true, true, 0, millis);
        }
        final var output = result.output();
        return new FileOutcome(
                entry.name(),
                false,
                false,
                reparses(output),
                Javafmt.format(output, config).equals(output),
                overWidthLines(output),
                millis);
    }

    private static boolean reparses(final String output) {
        try {
            JavaParser.parseUnit(output);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int overWidthLines(final String output) {
        return (int) output.lines().filter(line -> line.length() > LINE_WIDTH).count();
    }

    long count(final java.util.function.Predicate<FileOutcome> property) {
        return outcomes.stream().filter(property).count();
    }

    List<String> names(final java.util.function.Predicate<FileOutcome> property) {
        return outcomes.stream().filter(property).map(FileOutcome::name).toList();
    }

    Optional<FileOutcome> slowest() {
        return outcomes.stream().max((a, b) -> Long.compare(a.millis(), b.millis()));
    }

    /**
    * What one corpus file did. {@code refused} and {@code unparseable} are mutually exclusive and
    * both mean javafmt handed the source back untouched, so the layout properties below are only
    * meaningful — and only counted — for a file that actually produced output.
    */
    record FileOutcome(String name, boolean unparseable, boolean refused, boolean reparses, boolean idempotent, int overWidthLines, long millis) {

        boolean formatted() {
            return !unparseable && !refused;
        }
    }
}
