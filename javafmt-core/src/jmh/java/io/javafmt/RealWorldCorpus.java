package io.javafmt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Real Java sources for the benchmarks, as opposed to {@link BenchmarkCorpus}'s three hand-written
 * files. The corpus is javafmt's own {@code javafmt-core} main sources: real production code with
 * the shapes a formatter actually meets — long generic signatures, switch pattern dispatch, nested
 * records, heavy javadoc — with no download step and no third-party license to carry.
 *
 * <p>The build points {@code javafmt.corpus} at the source directory. Sources are read once during
 * benchmark setup, never inside a measured method.
 *
 * <p>{@link #dirty} is the corpus as an unformatted codebase would arrive: the transform strips
 * {@code final } everywhere, which makes {@code FinalLocalVariable} and {@code FinalParameters}
 * fire on nearly every method. That matters because those rules fire on most real unformatted
 * input, so the dirty numbers — not the clean ones — are what a first run over a codebase costs.
 */
public final class RealWorldCorpus {

    private static final String CORPUS_PROPERTY = "javafmt.corpus";

    private static final String SRC_ZIP_PROPERTY = "javafmt.corpus.srczip";

    public static List<String> clean(final int limit) {
        final var root = System.getProperty(CORPUS_PROPERTY);
        if (root == null) {
            throw new IllegalStateException(CORPUS_PROPERTY + " is not set; run the benchmarks through the Gradle jmh task");
        }
        try (final var walk = Files.walk(Path.of(root))) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(limit)
                    .map(RealWorldCorpus::read)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<String> dirty(final int limit) {
        return clean(limit).stream().map(source -> source.replace("final ", "")).toList();
    }

    public static String jdk(final String file) {
        final var srcZip = System.getProperty(SRC_ZIP_PROPERTY);
        if (srcZip == null) {
            throw new IllegalStateException(SRC_ZIP_PROPERTY + " is not set; run the benchmarks through the Gradle jmh task");
        }
        try (final var zip = FileSystems.newFileSystem(Path.of(srcZip))) {
            return read(zip.getPath("java.base", file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private RealWorldCorpus() {}
}
