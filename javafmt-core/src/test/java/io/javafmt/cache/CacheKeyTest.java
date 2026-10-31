package io.javafmt.cache;

import io.javafmt.JavafmtConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyTest {

    @Test
    void aParseOnlyConfigKeysWithoutAClasspath() {
        assertThat(CacheKey.of(JavafmtConfig.defaults())).isEqualTo(new CacheKey(true, true, false, "", 25));
    }

    @Test
    void anAttributedConfigKeysOnItsClasspath() {
        final var config = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        assertThat(CacheKey.of(config)).isEqualTo(new CacheKey(true, true, true, "/lib/a.jar", 25));
    }

    @Test
    void configsDifferingOnlyInTheirReleaseKeyDifferently() {
        assertThat(CacheKey.of(JavafmtConfig.defaults().withRelease(21))).isNotEqualTo(CacheKey.of(JavafmtConfig.defaults()));
    }

    @Test
    void theFingerprintIsStableSoExistingCacheFilesStayValid() {
        assertThat(CacheKey.of(JavafmtConfig.defaults()).fingerprint()).isEqualTo("1e3ed9cb952895df");
        final var config = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        assertThat(CacheKey.of(config).fingerprint()).isEqualTo("e0c0800e07385061");
    }
}
