package io.javafmt;

import io.javafmt.cache.FormatCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedMemoryTest {
    private static final JavafmtConfig ANALYZE = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(""));

    private static final int MEGABYTE = 1 << 20;

    static Stream<Arguments> slicings() {
        return Stream.of(
                Arguments.of(List.of(1L, 2L, 3L), 6L, List.of(List.of(0, 1, 2))),
                Arguments.of(List.of(1L, 2L, 3L), 3L, List.of(List.of(0, 1), List.of(2))),
                Arguments.of(List.of(5L, 1L, 1L), 3L, List.of(List.of(0), List.of(1, 2))),
                Arguments.of(List.of(0L, 0L, 4L), 2L, List.of(List.of(0, 1), List.of(2))), Arguments.of(List.of(), 3L, List.of()));
    }

    @ParameterizedTest
    @MethodSource("slicings")
    void slicesAreContiguousAndStayWithinTheBudgetUnlessOneItemIsLargerThanIt(final List<Long> weights,
                                                                              final long budget,
                                                                              final List<List<Integer>> expected) {
        assertThat(BatchFormatter.slices(weights, budget)).isEqualTo(expected);
    }

    @Test
    void aRunLargerThanOneAttributionBatchIsAttributedOneBatchAtATime() {
        final var padding = "/*" + "x".repeat(MEGABYTE + MEGABYTE / 2) + "*/\n";
        final var box = "package storage;\n\npublic class Box {}\n" + padding;
        final var sameBatch = "package shipping;\n\nimport storage.*;\n\nclass Crate {\n    Box box;\n}\n" + padding;
        final var nextBatch = "package shipping;\n\nimport storage.*;\n\nclass Pallet {\n    Box box;\n}\n" + padding;
        final var filler = "class Filler {}\n" + padding;
        final var results = Javafmt.formatAll(List.of(box, sameBatch, nextBatch, filler), ANALYZE, 2);
        assertThat(results.get(1).output()).contains("import storage.Box;");
        assertThat(results.get(2).output()).contains("import storage.*;");
    }

    static Stream<Arguments> batchings() {
        final var oneDirectory = IntStream.range(0, 300).mapToObj(_ -> "orders").toList();
        final var twoDirectories = IntStream.range(0, 170).mapToObj(i -> i < 70 ? "orders" : "invoices").toList();
        final var smallDirectories = IntStream.range(0, 40).mapToObj(i -> "directory" + i / 4).toList();
        return Stream.of(
                Arguments.of(oneDirectory, List.of(ranges(0, 128), ranges(128, 256), ranges(256, 300))),
                Arguments.of(twoDirectories, List.of(ranges(0, 70), ranges(70, 170))), Arguments.of(smallDirectories, List.of(ranges(0, 40))));
    }

    @ParameterizedTest
    @MethodSource("batchings")
    void attributionBatchesDependOnlyOnTheFilesAndTheirDirectories(final List<String> directories, final List<List<Integer>> expected) {
        assertThat(BatchFormatter.batches(directories.stream().map(_ -> 1000L).toList(), directories)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("batchings")
    void attributionBatchesDoNotMoveWhenFormattingChangesTheSizeOfAFile(final List<String> directories, final List<List<Integer>> expected) {
        final var sizes = IntStream.range(0, directories.size()).mapToObj(i -> 900L + i % 7 * 50).toList();
        assertThat(BatchFormatter.batches(sizes, directories)).isEqualTo(expected);
    }

    @Test
    void aBatchLargerThanTheAttributionBudgetIsHalvedUntilItFits() {
        final var directories = IntStream.range(0, 8).mapToObj(_ -> "orders").toList();
        final var sizes = IntStream.range(0, 8).mapToObj(_ -> (long) MEGABYTE).toList();
        assertThat(BatchFormatter.batches(sizes, directories)).containsExactly(ranges(0, 4), ranges(4, 8));
    }

    private static List<Integer> ranges(final int from, final int to) {
        return IntStream.range(from, to).boxed().toList();
    }

    @Test
    void filesAreFormattedSliceBySliceAsTheResultsAreConsumed(@TempDir final Path dir) throws IOException {
        final var padding = "/*" + "x".repeat(3 * MEGABYTE) + "*/\n";
        final var paths = IntStream.range(0, 3).mapToObj(i -> dir.resolve("Order" + i + ".java")).toList();
        paths.forEach(path -> write(path, "class " + path.getFileName().toString().replace(".java", "") + " {}\n" + padding));
        final var asked = ConcurrentHashMap.<Path>newKeySet();
        final var cache = new FormatCache() {
            @Override
            public boolean isUpToDate(final Path file, final String currentContent) {
                asked.add(file);
                return false;
            }

            @Override
            public void recordFormatted(final Path file, final String postFormatContent) {}

            @Override
            public void save() {}
        };
        try (final var results = Javafmt.formatFilesLazily(paths, ANALYZE, cache, 2)) {
            final var first = results.iterator().next();
            assertThat(first.path()).isEqualTo(paths.getFirst());
            assertThat(asked).containsExactly(paths.getFirst());
        }
    }

    @Test
    void theLazyResultsAreTheResultsInPathOrder(@TempDir final Path dir) {
        final var paths = IntStream.range(0, 40).mapToObj(i -> dir.resolve("Invoice" + i + ".java")).toList();
        paths.forEach(path -> write(path, "class   " + path.getFileName().toString().replace(".java", "") + " {}\n"));
        try (final var results = Javafmt.formatFilesLazily(paths, JavafmtConfig.defaults(), FormatCache.noop(), 4)) {
            assertThat(results.toList()).isEqualTo(Javafmt.formatFiles(paths, JavafmtConfig.defaults(), FormatCache.noop(), 4));
        }
    }

    @Test
    void batchesAttributedAtTheSameTimeGiveTheResultsOfBatchesAttributedOneAfterAnother(@TempDir final Path dir) {
        final var padding = "/*" + "x".repeat(3 * MEGABYTE) + "*/\n";
        final var paths = IntStream.range(0, 3).mapToObj(i -> dir.resolve("Customer" + i + ".java")).toList();
        paths.forEach(path -> write(path, "class   " + path.getFileName().toString().replace(".java", "") + " {   }\n" + padding));
        try (
                final var together = BatchFormatter.formatFilesLazily(paths, ANALYZE, FormatCache.noop(), 4, 3);
                final var oneAfterAnother = BatchFormatter.formatFilesLazily(paths, ANALYZE, FormatCache.noop(), 4, 1)) {
            assertThat(together.toList()).isEqualTo(oneAfterAnother.toList());
        }
    }

    @Test
    void runningOutOfMemoryOnAFormatThreadEndsTheRun() {
        try (final var pool = Executors.newSingleThreadExecutor()) {
            assertThatThrownBy(
                    () -> BatchFormatter.Renderer.on(pool)
                            .renderAll(
                                    List.of("class A {}\n"),
                                    List.of(() -> {
                                        throw new OutOfMemoryError("Java heap space");
                                    })))
                    .isInstanceOf(OutOfMemoryError.class);
        }
    }

    private static void write(final Path path, final String content) {
        try {
            Files.writeString(path, content);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
