package io.javafmt.cache;

import io.javafmt.JavafmtConfig;
import io.javafmt.ProjectFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyTest {
    @Test
    void aParseOnlyConfigKeysWithoutAClasspath() {
        assertThat(CacheKey.of(JavafmtConfig.defaults())).isEqualTo(new CacheKey(true, true, false, "", 25, List.of()));
    }

    @Test
    void anAttributedConfigKeysOnItsClasspath() {
        final var config = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        assertThat(CacheKey.of(config)).isEqualTo(new CacheKey(true, true, true, "/lib/a.jar", 25, List.of()));
    }

    @Test
    void configsDifferingOnlyInTheirReleaseKeyDifferently() {
        assertThat(CacheKey.of(JavafmtConfig.defaults().withRelease(21))).isNotEqualTo(CacheKey.of(JavafmtConfig.defaults()));
    }

    @Test
    void theProjectFilesThatGovernTheRunTakePartInTheKey() {
        final var config = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        final var skipping = new ProjectFile.Settings("/repository/.javafmt", Set.of("LocalVarUseVar"));
        final var elsewhere = new ProjectFile.Settings("/other/.javafmt", Set.of("LocalVarUseVar"));
        final var keys = Stream.of(
                List.<ProjectFile>of(), List.<ProjectFile>of(new ProjectFile.Settings("/repository/.javafmt", Set.of())), List.<ProjectFile>of(skipping),
                List.<ProjectFile>of(elsewhere), List.<ProjectFile>of(skipping, elsewhere))
                .map(projectFiles -> CacheKey.of(config, projectFiles).fingerprint())
                .toList();
        assertThat(keys).doesNotHaveDuplicates();
        assertThat(CacheKey.of(config, List.of(new ProjectFile.None()))).isEqualTo(CacheKey.of(config));
        assertThat(CacheKey.of(config, List.of(elsewhere, skipping))).isEqualTo(CacheKey.of(config, List.of(skipping, elsewhere)));
    }

    @Test
    void theFingerprintIsStableSoExistingCacheFilesStayValid() {
        assertThat(CacheKey.of(JavafmtConfig.defaults()).fingerprint()).isEqualTo("1e3ed9cb952895df");
        final var config = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        assertThat(CacheKey.of(config).fingerprint()).isEqualTo("e0c0800e07385061");
    }
}
