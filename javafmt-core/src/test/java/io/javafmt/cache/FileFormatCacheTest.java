package io.javafmt.cache;

import io.javafmt.JavafmtConfig;
import io.javafmt.JavafmtVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileFormatCacheTest {

    private static final CacheKey KEY = CacheKey.of(JavafmtConfig.defaults());

    @TempDir
    Path dir;

    private Path cacheFile;

    @BeforeEach
    void locateTheCacheFile() {
        cacheFile = dir.resolve(".javafmt-cache");
    }

    @Test
    void emptyCache_isUpToDate_returnsFalseForAnyFile() {
        final var cache = FileFormatCache.load(cacheFile, KEY);
        final var path = dir.resolve("A.java");
        assertThat(cache.isUpToDate(path, "class A {}")).isFalse();
    }

    @Test
    void afterRecord_isUpToDate_returnsTrueForSameContent() {
        final var cache = FileFormatCache.load(cacheFile, KEY);
        final var path = dir.resolve("A.java");
        cache.recordFormatted(path, "class A {}\n");
        assertThat(cache.isUpToDate(path, "class A {}\n")).isTrue();
    }

    @Test
    void afterRecord_isUpToDate_returnsFalseWhenContentDiffers() {
        final var cache = FileFormatCache.load(cacheFile, KEY);
        final var path = dir.resolve("A.java");
        cache.recordFormatted(path, "class A {}\n");
        assertThat(cache.isUpToDate(path, "class A { int x; }\n")).isFalse();
    }

    @Test
    void saveAndReload_preservesEntries() throws Exception {
        final var content = "class A {}\n";
        // The file has to exist on disk: save() prunes entries for files that have been deleted.
        final var path = Files.writeString(dir.resolve("A.java"), content);
        final var first = FileFormatCache.load(cacheFile, KEY);
        first.recordFormatted(path, content);
        first.save();
        assertThat(Files.exists(cacheFile)).isTrue();
        final var reloaded = FileFormatCache.load(cacheFile, KEY);
        assertThat(reloaded.isUpToDate(path, content)).isTrue();
        assertThat(reloaded.isUpToDate(path, "different\n")).isFalse();
    }

    @Test
    void save_isAtomic_doesNotLeaveTempArtifact() throws Exception {
        final var cache = FileFormatCache.load(cacheFile, KEY);
        cache.recordFormatted(dir.resolve("A.java"), "class A {}\n");
        cache.save();
        try (final var list = Files.list(dir)) {
            assertThat(list.map(Path::getFileName).map(Path::toString)).containsExactly(".javafmt-cache");
        }
    }

    @Test
    void load_corruptedFile_returnsEmptyCache() throws Exception {
        Files.writeString(cacheFile, "this is not\nvalid cache content !!!\n", StandardCharsets.UTF_8);
        final var cache = FileFormatCache.load(cacheFile, KEY);
        // any access on a corrupted cache should behave as empty
        assertThat(cache.isUpToDate(dir.resolve("A.java"), "class A {}\n")).isFalse();
    }

    @Test
    void recordFormatted_overwritesPreviousHashForSamePath() {
        final var cache = FileFormatCache.load(cacheFile, KEY);
        final var path = dir.resolve("A.java");
        cache.recordFormatted(path, "v1\n");
        cache.recordFormatted(path, "v2\n");
        assertThat(cache.isUpToDate(path, "v1\n")).isFalse();
        assertThat(cache.isUpToDate(path, "v2\n")).isTrue();
    }

    @Test
    void concurrentRecordFormatted_isThreadSafe() throws Exception {
        final var cache = FileFormatCache.load(cacheFile, KEY);
        final var paths = IntStream.range(0, 200)
                .mapToObj(i -> dir.resolve("F" + i + ".java"))
                .toList();
        final var contents = paths.stream()
                .map(p -> "class "
                        + p.getFileName().toString().replace(".java", "")
                        + " {}\n")
                .toList();
        try (final var pool = Executors.newFixedThreadPool(8)) {
            final var futures = IntStream.range(0, paths.size())
                    .mapToObj(i -> pool.submit(() -> {
                        cache.recordFormatted(paths.get(i), contents.get(i));
                        return null;
                    }))
                    .toList();
            for (final var f : futures) {
                f.get();
            }
        }
        for (var i = 0; i < paths.size(); i++) {
            assertThat(cache.isUpToDate(paths.get(i), contents.get(i)))
                    .as("file %s should be up to date", paths.get(i))
                    .isTrue();
        }
    }

    @Test
    void cacheWrittenByAnotherJavafmtVersionIsIgnored() throws Exception {
        // A formatting change in a new javafmt release makes every stored hash wrong. The entries
        // have to be discarded, not trusted, or the upgrade silently skips every file.
        final var path = dir.resolve("A.java");
        final var content = "class A {}\n";
        final var written = FileFormatCache.load(cacheFile, KEY);
        written.recordFormatted(path, content);
        written.save();
        final var stale = Files.readString(cacheFile, StandardCharsets.UTF_8).replace(JavafmtVersion.VERSION, "0.0.0-not-this-version");
        Files.writeString(cacheFile, stale, StandardCharsets.UTF_8);
        assertThat(FileFormatCache.load(cacheFile, KEY).isUpToDate(path, content))
                .isFalse();
    }

    @Test
    void headerCarriesTheFormatterVersion() throws Exception {
        final var cache = FileFormatCache.load(cacheFile, KEY);
        cache.recordFormatted(dir.resolve("A.java"), "class A {}\n");
        cache.save();
        assertThat(
                        Files.readString(cacheFile, StandardCharsets.UTF_8)
                                .lines()
                                .findFirst())
                .hasValueSatisfying(header -> assertThat(header)
                        .startsWith("# javafmt cache")
                        .contains(JavafmtVersion.VERSION));
    }

    @Test
    void aCacheWrittenByARunWithADifferentConfigurationIsIgnored() throws Exception {
        final var content = "class A {}\n";
        final var path = Files.writeString(dir.resolve("A.java"), content);
        final var plainRun = FileFormatCache.load(cacheFile, KEY);
        plainRun.recordFormatted(path, content);
        plainRun.save();
        final var analyzeRun = FileFormatCache.load(
                cacheFile, CacheKey.of(JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/some/classpath.jar"))));
        assertThat(analyzeRun.isUpToDate(path, content)).isFalse();
    }

    @Test
    void theHeaderCarriesTheConfigurationFingerprint() throws Exception {
        final var cache = FileFormatCache.load(cacheFile, KEY);
        cache.recordFormatted(dir.resolve("A.java"), "class A {}\n");
        cache.save();
        assertThat(
                        Files.readString(cacheFile, StandardCharsets.UTF_8)
                                .lines()
                                .findFirst())
                .hasValueSatisfying(header -> assertThat(header).endsWith(KEY.fingerprint()));
    }

    @Test
    void twoRunsSavingToTheSameCachePathBothSucceedAndLeaveAValidCache() throws Exception {
        final var content = "class A {}\n";
        final var path = Files.writeString(dir.resolve("A.java"), content);
        final var saves = IntStream.range(0, 8)
                .<Callable<Void>>mapToObj(_ -> () -> {
                    final var cache = FileFormatCache.load(cacheFile, KEY);
                    cache.recordFormatted(path, content);
                    cache.save();
                    return null;
                })
                .toList();
        try (final var pool = Executors.newFixedThreadPool(4)) {
            for (final var result : pool.invokeAll(saves)) {
                assertThat(result.get()).isNull();
            }
        }
        assertThat(FileFormatCache.load(cacheFile, KEY).isUpToDate(path, content))
                .isTrue();
        try (final var list = Files.list(dir)) {
            assertThat(list.map(Path::getFileName).map(Path::toString)).containsExactlyInAnyOrder(".javafmt-cache", "A.java");
        }
    }

    @Test
    void aFailedSaveLeavesNoTempFile() throws Exception {
        Files.createDirectories(cacheFile);
        Files.writeString(cacheFile.resolve("occupant"), "x");
        final var path = Files.writeString(dir.resolve("A.java"), "class A {}\n");
        final var cache = FileFormatCache.load(cacheFile, KEY);
        cache.recordFormatted(path, "class A {}\n");
        assertThatThrownBy(cache::save).isInstanceOf(UncheckedIOException.class);
        try (final var list = Files.list(dir)) {
            assertThat(list.map(Path::getFileName).map(Path::toString)).containsExactlyInAnyOrder(".javafmt-cache", "A.java");
        }
    }

    @Test
    void aReadOnlyCacheFileIsReplacedBySave() throws Exception {
        final var cacheFile = Files.writeString(dir.resolve(".javafmt-cache"), "stale\n");
        assertThat(cacheFile.toFile().setReadOnly()).isTrue();
        final var content = "class A {}\n";
        final var path = Files.writeString(dir.resolve("A.java"), content);
        final var cache = FileFormatCache.load(cacheFile, KEY);
        cache.recordFormatted(path, content);
        cache.save();
        assertThat(FileFormatCache.load(cacheFile, KEY).isUpToDate(path, content))
                .isTrue();
    }

    @Test
    void savePrunesEntriesForFilesThatNoLongerExist() throws Exception {
        final var kept = Files.writeString(dir.resolve("Kept.java"), "class Kept {}\n");
        final var deleted = dir.resolve("Deleted.java");
        final var cache = FileFormatCache.load(cacheFile, KEY);
        cache.recordFormatted(kept, "class Kept {}\n");
        cache.recordFormatted(deleted, "class Deleted {}\n");
        cache.save();
        final var body = Files.readString(cacheFile, StandardCharsets.UTF_8);
        assertThat(body)
                .contains(kept.toAbsolutePath().toString())
                .doesNotContain(deleted.toAbsolutePath().toString());
    }

    @Test
    void sha256IsTheHexDigestOfTheUtf8Bytes() {
        assertThat(FileFormatCache.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
