package io.javafmt.corpus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The corpus the gate runs against: the JDK's own {@code java.base} sources, read straight out of
 * the {@code lib/src.zip} that ships with the toolchain. Nothing is downloaded and nothing is
 * checked in — the build hands the test the path of the JDK it already compiles against.
 *
 * <p>The whole module is the point: 3,400 files of ordinary, heavily-generic, heavily-commented
 * production Java that nobody wrote to suit javafmt.
 */
final class JdkCorpus {

    private static final String MODULE = "java.base";

    static List<Entry> load(final Path srcZip) {
        try (var zip = FileSystems.newFileSystem(srcZip, Map.<String, Object>of())) {
            return Stream.of(zip.getPath(MODULE))
                    .filter(Files::isDirectory)
                    .flatMap(JdkCorpus::javaFilesUnder)
                    .map(JdkCorpus::read)
                    .sorted((a, b) -> a.name().compareTo(b.name()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the JDK source corpus at " + srcZip, e);
        }
    }

    private static Stream<Path> javaFilesUnder(final Path dir) {
        try (var walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + dir, e);
        }
    }

    private static Entry read(final Path file) {
        try {
            return new Entry(file.toString(), new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    /** One entry per corpus file: its path inside {@code src.zip} and its source text. */
    record Entry(String name, String source) {}

    private JdkCorpus() {}
}
