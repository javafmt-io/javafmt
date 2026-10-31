package io.javafmt.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class NoopFormatCacheTest {
    @Test
    void isUpToDate_alwaysReturnsFalse(@TempDir final Path dir) {
        final var cache = FormatCache.noop();
        cache.recordFormatted(dir.resolve("A.java"), "class A {}");
        assertThat(cache.isUpToDate(dir.resolve("A.java"), "class A {}")).isFalse();
    }

    @Test
    void save_doesNotThrow() {
        final var cache = FormatCache.noop();
        assertThatNoException().isThrownBy(cache::save);
    }
}
