package io.javafmt.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.JavafmtConfig;
import io.javafmt.JavafmtVersion;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileFormatCacheTest {

    private static final CacheKey KEY = CacheKey.of(JavafmtConfig.defaults());

    @Test
    void emptyCache_isUpToDate_returnsFalseForAnyFile(@TempDir final Path dir) {
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var cache = FileFormatCache.load(cacheFile, KEY);
        final var path = dir.resolve("A.java");
        assertThat(cache.isUpToDate(path, "class A {}")).isFalse();
    }

    @Test
    void afterRecord_isUpToDate_returnsTrueForSameContent(@TempDir final Path dir) {
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var cache = FileFormatCache.load(cacheFile, KEY);
        final var path = dir.resolve("A.java");
        cache.recordFormatted(path, "class A {}\n");
        assertThat(cache.isUpToDate(path, "class A {}\n")).isTrue();
    }

    @Test
    void afterRecord_isUpToDate_returnsFalseWhenContentDiffers(@TempDir final Path dir) {
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var cache = FileFormatCache.load(cacheFile, KEY);
        final var path = dir.resolve("A.java");
        cache.recordFormatted(path, "class A {}\n");
        assertThat(cache.isUpToDate(path, "class A { int x; }\n")).isFalse();
    }

    @Test
    void saveAndReload_preservesEntries(@TempDir final Path dir) throws Exception {
        final var cacheFile = dir.resolve(".javafmt-cache");
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
    void save_isAtomic_doesNotLeaveTempArtifact(@TempDir final Path dir) throws Exception {
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var cache = FileFormatCache.load(cacheFile, KEY);
        cache.recordFormatted(dir.resolve("A.java"), "class A {}\n");
        cache.save();
        try (final var list = Files.list(dir)) {
            assertThat(list.map(Path::getFileName).map(Path::toString)).containsExactly(".javafmt-cache");
        }
    }

    @Test
    void load_corruptedFile_returnsEmptyCache(@TempDir final Path dir) throws Exception {
        final var cacheFile = dir.resolve(".javafmt-cache");
        Files.writeString(cacheFile, "this is not\nvalid cache content !!!\n", StandardCharsets.UTF_8);
        final var cache = FileFormatCache.load(cacheFile, KEY);
        // any access on a corrupted cache should behave as empty
        assertThat(cache.isUpToDate(dir.resolve("A.java"), "class A {}\n")).isFalse();
    }

    @Test
    void recordFormatted_overwritesPreviousHashForSamePath(@TempDir final Path dir) {
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var cache = FileFormatCache.load(cacheFile, KEY);
        final var path = dir.resolve("A.java");
        cache.recordFormatted(path, "v1\n");
        cache.recordFormatted(path, "v2\n");
        assertThat(cache.isUpToDate(path, "v1\n")).isFalse();
        assertThat(cache.isUpToDate(path, "v2\n")).isTrue();
    }

    @Test
    void concurrentRecordFormatted_isThreadSafe(@TempDir final Path dir) throws Exception {
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var cache = FileFormatCache.load(cacheFile, KEY);
        final var paths = java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> dir.resolve("F" + i + ".java"))
                .toList();
        final var contents = paths.stream()
                .map(p -> "class " + p.getFileName().toString().replace(".java", "") + " {}\n")
                .toList();
        final var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            final var futures = java.util.stream.IntStream.range(0, paths.size())
                    .mapToObj(
                            i -> pool.submit(() -> {
                                cache.recordFormatted(paths.get(i), contents.get(i));
                                return null;
                            }))
                    .toList();
            for (var f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        for (var i = 0; i < paths.size(); i++) {
            assertThat(cache.isUpToDate(paths.get(i), contents.get(i)))
                    .as("file %s should be up to date", paths.get(i))
                    .isTrue();
        }
    }

    @Test
    void cacheWrittenByAnotherJavafmtVersionIsIgnored(@TempDir final Path dir) throws Exception {
        // A formatting change in a new javafmt release makes every stored hash wrong. The entries
        // have to be discarded, not trusted, or the upgrade silently skips every file.
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var path = dir.resolve("A.java");
        final var content = "class A {}\n";
        final var written = FileFormatCache.load(cacheFile, KEY);
        written.recordFormatted(path, content);
        written.save();
        final var stale = Files.readString(cacheFile, StandardCharsets.UTF_8)
                .replace(JavafmtVersion.VERSION, "0.0.0-not-this-version");
        Files.writeString(cacheFile, stale, StandardCharsets.UTF_8);
        assertThat(FileFormatCache.load(cacheFile, KEY).isUpToDate(path, content)).isFalse();
    }

    @Test
    void headerCarriesTheFormatterVersion(@TempDir final Path dir) throws Exception {
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var cache = FileFormatCache.load(cacheFile, KEY);
        cache.recordFormatted(dir.resolve("A.java"), "class A {}\n");
        cache.save();
        assertThat(Files.readString(cacheFile, StandardCharsets.UTF_8).lines().findFirst())
                .hasValueSatisfying(header -> assertThat(header).startsWith("# javafmt cache").contains(JavafmtVersion.VERSION));
    }

    @Test
    void aCacheWrittenByARunWithADifferentConfigurationIsIgnored(@TempDir final Path dir) throws Exception {
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var content = "class A {}\n";
        final var path = Files.writeString(dir.resolve("A.java"), content);
        final var plainRun = FileFormatCache.load(cacheFile, KEY);
        plainRun.recordFormatted(path, content);
        plainRun.save();
        final var analyzeRun = FileFormatCache.load(cacheFile, CacheKey.ofAnalysis(JavafmtConfig.defaults(), "/some/cp.jar"));
        assertThat(analyzeRun.isUpToDate(path, content)).isFalse();
    }

    @Test
    void theHeaderCarriesTheConfigurationFingerprint(@TempDir final Path dir) throws Exception {
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var cache = FileFormatCache.load(cacheFile, KEY);
        cache.recordFormatted(dir.resolve("A.java"), "class A {}\n");
        cache.save();
        assertThat(Files.readString(cacheFile, StandardCharsets.UTF_8).lines().findFirst())
                .hasValueSatisfying(header -> assertThat(header).endsWith(KEY.fingerprint()));
    }

    /** Two javafmt runs sharing one cache path must not write over each other's half-built file. */
    @Test
    void saveSucceedsWhenSomethingElseHoldsTheObviousTempName(@TempDir final Path dir) throws Exception {
        final var cacheFile = dir.resolve(".javafmt-cache");
        final var content = "class A {}\n";
        final var path = Files.writeString(dir.resolve("A.java"), content);
        Files.createDirectory(dir.resolve(".javafmt-cache.tmp"));
        final var cache = FileFormatCache.load(cacheFile, KEY);
        cache.recordFormatted(path, content);
        cache.save();
        assertThat(FileFormatCache.load(cacheFile, KEY).isUpToDate(path, content)).isTrue();
    }

    @Test
    void savePrunesEntriesForFilesThatNoLongerExist(@TempDir final Path dir) throws Exception {
        final var cacheFile = dir.resolve(".javafmt-cache");
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
}
