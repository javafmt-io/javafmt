package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.CommentToken;
import io.javafmt.parser.JavaParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DogfoodTest {

    @Test
    void formatPreservesEveryCommentInJavafmtCoreSource() throws IOException {
        final var batch = loadJavafmtCoreSources();
        final var formatted = batchFormat(batch.sources);
        final var report = new StringBuilder();
        var lostTotal = 0;
        for (var i = 0; i < batch.paths.size(); i++) {
            final var before = CommentScanner.scan(batch.sources.get(i));
            final var afterTexts = CommentScanner.scan(formatted.get(i)).stream().map(CommentToken::text).toList();
            final var missing = before.stream()
                    .map(CommentToken::text)
                    .filter(t -> !afterTexts.contains(t))
                    .toList();
            if (!missing.isEmpty()) {
                lostTotal += missing.size();
                report.append(batch.root.relativize(batch.paths.get(i)))
                        .append(": lost ")
                        .append(missing.size())
                        .append(" comment(s):\n");
                missing.forEach(m -> report.append("    ").append(m.length() > 80 ? m.substring(0, 80) + "..." : m).append('\n'));
            }
        }
        final var lost = lostTotal;
        assertThat(lost)
                .withFailMessage("Lost %d comment(s) when formatting javafmt's own source:%n%s", lost, report)
                .isZero();
    }

    @Test
    void formatProducesSyntacticallyValidOutputForEveryJavafmtCoreSourceFile() throws IOException {
        final var batch = loadJavafmtCoreSources();
        final var formatted = batchFormat(batch.sources);
        final var reparseOutcomes = JavaParser.parseUnits(formatted);
        final var report = new StringBuilder();
        var failures = 0;
        for (var i = 0; i < batch.paths.size(); i++) {
            if (reparseOutcomes.get(i) instanceof io.javafmt.parser.Staged.Failed<?>(var failure)) {
                failures++;
                report.append(batch.root.relativize(batch.paths.get(i)))
                        .append(": formatted output failed to re-parse: ")
                        .append(failure.getMessage())
                        .append('\n');
            }
        }
        final var count = failures;
        assertThat(count)
                .withFailMessage("%d javafmt-core file(s) formatted to unparseable output:%n%s", count, report)
                .isZero();
    }

    @Test
    void formatPreservesTheTokenStreamOfEveryJavafmtCoreSourceFile() throws IOException {
        // Stronger than the comment check above and the re-parse check below: this asserts
        // that Doc build and print changed nothing but layout, for every file in the module.
        final var batch = loadJavafmtCoreSources();
        final var results = Javafmt.formatAll(
                batch.sources,
                JavafmtConfig.defaults().withVerifyOutput(true),
                Runtime.getRuntime().availableProcessors());
        final var report = new StringBuilder();
        var failures = 0;
        for (var i = 0; i < batch.paths.size(); i++) {
            final var result = results.get(i);
            final var internal = result.diagnostics()
                    .stream()
                    .filter(d -> d instanceof Diagnostic.InternalError)
                    .toList();
            if (!internal.isEmpty()) {
                failures++;
                report.append(batch.root.relativize(batch.paths.get(i)))
                        .append(": ")
                        .append(internal.getFirst().message())
                        .append('\n');
            }
        }
        final var count = failures;
        assertThat(count)
                .withFailMessage("%d javafmt-core file(s) did not survive output verification:%n%s", count, report)
                .isZero();
    }

    @Test
    void formatRoundTripsEveryJavafmtCoreSourceFile() throws IOException {
        final var batch = loadJavafmtCoreSources();
        final var once = batchFormat(batch.sources);
        final var twice = batchFormat(once);
        final var report = new StringBuilder();
        var failures = 0;
        for (var i = 0; i < batch.paths.size(); i++) {
            if (!once.get(i).equals(twice.get(i))) {
                failures++;
                report.append(batch.root.relativize(batch.paths.get(i))).append(": format(format(x)) != format(x)\n");
            }
        }
        final var count = failures;
        assertThat(count)
                .withFailMessage("%d javafmt-core file(s) failed to round-trip:%n%s", count, report)
                .isZero();
    }

    @Test
    void formatRoundTripsEveryJavafmtCoreTestSourceFile() throws IOException {
        // Regression for the text-block convergence bug: test sources are heavy users of
        // text blocks, and the prior renderer re-indented their interior lines on every
        // pass. This was the failure mode that prevented Spotless from converging on
        // ~36 *Test.java files. Keep this assertion green to prevent re-introduction.
        final var batch = loadJavafmtCoreTestSources();
        final var once = batchFormat(batch.sources);
        final var twice = batchFormat(once);
        final var report = new StringBuilder();
        var failures = 0;
        for (var i = 0; i < batch.paths.size(); i++) {
            if (!once.get(i).equals(twice.get(i))) {
                failures++;
                report.append(batch.root.relativize(batch.paths.get(i))).append(": format(format(x)) != format(x)\n");
            }
        }
        final var count = failures;
        assertThat(count)
                .withFailMessage("%d javafmt-core test file(s) failed to round-trip:%n%s", count, report)
                .isZero();
    }

    private static JavafmtCoreSources loadJavafmtCoreSources() throws IOException {
        return loadSourcesFrom(Paths.get("src/main/java").toAbsolutePath());
    }

    private static JavafmtCoreSources loadJavafmtCoreTestSources() throws IOException {
        return loadSourcesFrom(Paths.get("src/test/java").toAbsolutePath());
    }

    private static JavafmtCoreSources loadSourcesFrom(final Path root) throws IOException {
        assertThat(root).exists();
        try (final Stream<Path> stream = Files.walk(root)) {
            final var paths = stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            final var sources = paths.stream().map(DogfoodTest::readString).toList();
            return new JavafmtCoreSources(root, paths, sources);
        }
    }

    private static List<String> batchFormat(final List<String> sources) {
        return Javafmt.formatAll(sources, JavafmtConfig.defaults(), Runtime.getRuntime().availableProcessors())
                .stream()
                .map(FormatResult::output)
                .toList();
    }

    private static String readString(final Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record JavafmtCoreSources(Path root, List<Path> paths, List<String> sources) {}
}
