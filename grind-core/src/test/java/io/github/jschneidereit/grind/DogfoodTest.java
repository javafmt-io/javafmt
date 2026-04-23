package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.parser.CommentScanner;
import io.github.jschneidereit.grind.parser.CommentToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DogfoodTest {

    @Test
    void formatPreservesEveryCommentInGrindCoreSource() throws IOException {
        final var grindCoreMain = Paths.get("src/main/java").toAbsolutePath();
        assertThat(grindCoreMain).exists();

        final var report = new StringBuilder();
        final var lostTotal = new int[] {0};

        try (final Stream<Path> stream = Files.walk(grindCoreMain)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                .sorted()
                .forEach(p -> {
                    final var source = readString(p);
                    final var before = CommentScanner.scan(source);
                    final String formatted;
                    try {
                        formatted = Grind.format(source);
                    } catch (final RuntimeException e) {
                        report.append(grindCoreMain.relativize(p))
                            .append(": threw ").append(e.getClass().getSimpleName())
                            .append(" (pre-existing, non-comment issue): ").append(e.getMessage())
                            .append('\n');
                        return;
                    }
                    final var afterTexts = CommentScanner.scan(formatted).stream()
                        .map(CommentToken::text)
                        .toList();
                    final var missing = before.stream()
                        .map(CommentToken::text)
                        .filter(t -> !afterTexts.contains(t))
                        .toList();
                    if (!missing.isEmpty()) {
                        lostTotal[0] += missing.size();
                        report.append(grindCoreMain.relativize(p))
                            .append(": lost ").append(missing.size())
                            .append(" comment(s):\n");
                        missing.forEach(m -> report.append("    ")
                            .append(m.length() > 80 ? m.substring(0, 80) + "..." : m)
                            .append('\n'));
                    }
                });
        }

        assertThat(lostTotal[0])
            .withFailMessage("Lost %d comment(s) when formatting grind's own source:%n%s", lostTotal[0], report)
            .isZero();
    }

    @Test
    void formatRoundTripsEveryGrindCoreSourceFile() throws IOException {
        final var grindCoreMain = Paths.get("src/main/java").toAbsolutePath();
        assertThat(grindCoreMain).exists();

        final var report = new StringBuilder();
        final var failures = new int[] {0};

        try (final Stream<Path> stream = Files.walk(grindCoreMain)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                .sorted()
                .forEach(p -> {
                    final var source = readString(p);
                    final String once;
                    final String twice;
                    try {
                        once = Grind.format(source);
                        twice = Grind.format(once);
                    } catch (final RuntimeException e) {
                        failures[0]++;
                        report.append(grindCoreMain.relativize(p))
                            .append(": threw ").append(e.getClass().getSimpleName())
                            .append(": ").append(e.getMessage())
                            .append('\n');
                        return;
                    }
                    if (!once.equals(twice)) {
                        failures[0]++;
                        report.append(grindCoreMain.relativize(p))
                            .append(": format(format(x)) != format(x)\n");
                    }
                });
        }

        assertThat(failures[0])
            .withFailMessage("%d grind-core file(s) failed to round-trip:%n%s", failures[0], report)
            .isZero();
    }

    private static String readString(final Path p) {
        try {
            return Files.readString(p);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
