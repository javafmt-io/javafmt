package io.javafmt.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.JavafmtConfig;
import org.junit.jupiter.api.Test;

class CacheKeyTest {

    @Test
    void aParseOnlyConfigKeysWithoutAClasspath() {
        assertThat(CacheKey.of(JavafmtConfig.defaults())).isEqualTo(new CacheKey(true, true, false, ""));
    }

    @Test
    void anAttributedConfigKeysOnItsClasspath() {
        final var config = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        assertThat(CacheKey.of(config)).isEqualTo(new CacheKey(true, true, true, "/lib/a.jar"));
    }

    @Test
    void theFingerprintIsStableSoExistingCacheFilesStayValid() {
        assertThat(CacheKey.of(JavafmtConfig.defaults()).fingerprint()).isEqualTo("174fa54fb14bac9b");
        final var config = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        assertThat(CacheKey.of(config).fingerprint()).isEqualTo("70f4e09f9e6d87b8");
    }
}
