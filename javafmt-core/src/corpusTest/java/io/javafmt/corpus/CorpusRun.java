package io.javafmt.corpus;

import io.javafmt.Diagnostic;
import io.javafmt.FormatResult;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.parser.JavaParser;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One pass of javafmt over the whole corpus, kept as a value so every property the gate checks is
 * measured from the same run rather than reformatting 3,400 files once per assertion.
 */
record CorpusRun(List<FileOutcome> outcomes, long totalMillis) {

    private static final int LINE_WIDTH = 150;

    static CorpusRun over(final Path srcZip) {
        final var started = System.nanoTime();
        final var outcomes = JdkCorpus.load(srcZip).stream()
                .map(CorpusRun::formatOne)
                .toList();
        return new CorpusRun(outcomes, (System.nanoTime() - started) / 1_000_000);
    }

    private static FileOutcome formatOne(final JdkCorpus.Entry entry) {
        final var config = JavafmtConfig.defaults();
        final var started = System.nanoTime();
        final var result = Javafmt.formatWithResult(entry.source(), config);
        final var millis = (System.nanoTime() - started) / 1_000_000;
        if (reports(result, Diagnostic.ParseError.class)) {
            return new FileOutcome.Unparseable(entry.name(), millis);
        }
        if (reports(result, Diagnostic.InternalError.class)) {
            return new FileOutcome.Refused(entry.name(), millis);
        }
        if (reports(result, Diagnostic.LintError.class)) {
            return new FileOutcome.Rejected(entry.name(), millis);
        }
        final var output = result.output();
        return new FileOutcome.Formatted(
                entry.name(),
                millis,
                reparses(output),
                Javafmt.format(output, config).equals(output),
                overWidthLines(output));
    }

    private static boolean reports(final FormatResult result, final Class<? extends Diagnostic> kind) {
        return result.diagnostics().stream()
                .anyMatch(kind::isInstance);
    }

    private static boolean reparses(final String output) {
        try {
            JavaParser.parseUnit(output);
            return true;
        } catch (final RuntimeException _) {
            return false;
        }
    }

    private static int overWidthLines(final String output) {
        return (int) output.lines()
                .filter(line -> line.length() > LINE_WIDTH)
                .count();
    }

    long count(final java.util.function.Predicate<FileOutcome> property) {
        return outcomes.stream()
                .filter(property)
                .count();
    }

    List<String> names(final java.util.function.Predicate<FileOutcome> property) {
        return outcomes.stream()
                .filter(property)
                .map(FileOutcome::name)
                .toList();
    }

    Optional<FileOutcome> slowest() {
        return outcomes.stream()
                .max(Comparator.comparingLong(FileOutcome::millis));
    }

    List<FileOutcome> slowest(final int count) {
        return outcomes.stream()
                .sorted(Comparator.comparingLong(FileOutcome::millis).reversed())
                .limit(count)
                .toList();
    }

    sealed interface FileOutcome {

        String name();

        long millis();

        record Unparseable(String name, long millis) implements FileOutcome {}

        record Refused(String name, long millis) implements FileOutcome {}

        record Rejected(String name, long millis) implements FileOutcome {}

        record Formatted(String name, long millis, boolean reparses, boolean idempotent, int overWidthLines) implements FileOutcome {}
    }
}
