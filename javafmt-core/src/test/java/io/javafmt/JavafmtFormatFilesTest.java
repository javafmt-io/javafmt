package io.javafmt;

import io.javafmt.cache.CacheKey;
import io.javafmt.cache.FileFormatCache;
import io.javafmt.cache.FormatCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class JavafmtFormatFilesTest {

    private static final String UNFORMATTED = "class   Foo{\n\nvoid bar()  { }\n}\n";

    @Test
    void eachPathGetsItsOwnResultInInputOrder(@TempDir final Path dir) throws Exception {
        final var cache = FileFormatCache.load(dir.resolve("javafmt.cache"), CacheKey.of(JavafmtConfig.defaults()));
        final var cached = Files.writeString(dir.resolve("Cached.java"), "class Cached {}\n");
        cache.recordFormatted(cached, "class Cached {}\n");
        final var dirty = Files.writeString(dir.resolve("Dirty.java"), UNFORMATTED);
        final var missing = dir.resolve("Missing.java");
        final var empty = Files.writeString(dir.resolve("Empty.java"), "");
        final var results = Javafmt.formatFiles(List.of(cached, dirty, missing, empty), JavafmtConfig.defaults(), cache, 2);
        assertThat(results).hasSize(4);
        assertThat(results.get(0)).isEqualTo(new FileResult.UpToDate(cached));
        assertThat(results.get(1)).isEqualTo(new FileResult.Formatted(dirty, UNFORMATTED, Javafmt.formatWithResult(UNFORMATTED)));
        assertThat(results.get(2)).isInstanceOfSatisfying(FileResult.Unreadable.class, unreadable -> {
            assertThat(unreadable.path()).isEqualTo(missing);
            assertThat(unreadable.error()).isInstanceOf(NoSuchFileException.class);
        });
        assertThat(results.get(3)).isEqualTo(new FileResult.Formatted(empty, "", new FormatResult("", List.of())));
    }

    @ParameterizedTest
    @CsvSource({"'class A {}', true", "'class A  {}', false"})
    void aFormattedFileHasChangedWhenItsOutputDiffersFromItsSource(final String output, final boolean changed) {
        final var file = new FileResult.Formatted(Path.of("A.java"), "class A  {}", new FormatResult(output, List.of()));
        assertThat(file.changed()).isEqualTo(changed);
    }

    @Test
    void manyFilesStayAlignedWithTheirPaths(@TempDir final Path dir) throws Exception {
        final var paths = IntStream.range(0, 150)
                .mapToObj(i -> dir.resolve("F" + i + ".java"))
                .toList();
        IntStream.range(0, paths.size()).forEach(i -> write(paths.get(i), "class F" + i + " {   }\n"));
        final var results = Javafmt.formatFiles(paths, JavafmtConfig.defaults(), FormatCache.noop(), 4);
        assertThat(results).hasSameSizeAs(paths);
        IntStream.range(0, paths.size())
                .forEach(i -> assertThat(results.get(i))
                        .isEqualTo(new FileResult.Formatted(
                                paths.get(i), "class F" + i + " {   }\n", new FormatResult("class F" + i + " {}\n", List.of()))));
    }

    @Test
    void filesAreReadAndHashedOnTheFormatPoolRatherThanTheCallingThread(@TempDir final Path dir) {
        final var paths = IntStream.range(0, 8)
                .mapToObj(i -> dir.resolve("F" + i + ".java"))
                .toList();
        paths.forEach(path -> write(path, "class F {}\n"));
        final var hashingThreads = ConcurrentHashMap.<String>newKeySet();
        final var cache = new FormatCache() {
            @Override
            public boolean isUpToDate(final Path file, final String currentContent) {
                hashingThreads.add(Thread.currentThread().getName());
                return false;
            }

            @Override
            public void recordFormatted(final Path file, final String postFormatContent) {}

            @Override
            public void save() {}
        };
        Javafmt.formatFiles(paths, JavafmtConfig.defaults(), cache, 4);
        assertThat(hashingThreads)
                .isNotEmpty()
                .allSatisfy(name -> assertThat(name).startsWith("javafmt-"));
    }

    @Test
    void closingTheLazyResultsEndsTheFormatThreadsTheyStarted(@TempDir final Path dir) {
        final var before = formatThreads();
        final var paths = IntStream.range(0, 8)
                .mapToObj(i -> dir.resolve("F" + i + ".java"))
                .toList();
        paths.forEach(path -> write(path, UNFORMATTED));
        final var results = Javafmt.formatFilesLazily(paths, JavafmtConfig.defaults(), FormatCache.noop(), 4);
        assertThat(results.toList()).hasSameSizeAs(paths);
        final var started = formatThreads().stream()
                .filter(thread -> !before.contains(thread))
                .toList();
        results.close();
        assertThat(started)
                .isNotEmpty()
                .allMatch(thread -> endsWithin(thread, Duration.ofSeconds(10)));
    }

    private static Set<Thread> formatThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("javafmt-format-"))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean endsWithin(final Thread thread, final Duration timeout) {
        try {
            return thread.join(timeout);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void write(final Path path, final String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
