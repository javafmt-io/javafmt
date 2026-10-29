package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PathFilterTest {

    @Test
    void aFilterSurvivesSerializationAndComparesByItsGlobs() throws Exception {
        final var filter = PathFilter.ofGlobs(List.of("**/generated/**", "Legacy*.java"));
        final var bytes = new ByteArrayOutputStream();
        try (final var out = new ObjectOutputStream(bytes)) {
            out.writeObject(filter);
        }
        try (final var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            final var copy = (PathFilter) in.readObject();
            assertThat(copy).isEqualTo(filter).hasSameHashCodeAs(filter);
            assertThat(copy.excludes(Path.of("deep/dir/LegacyThing.java"))).isTrue();
            assertThat(copy.excludes(Path.of("src/main/Foo.java"))).isFalse();
        }
        assertThat(filter).isNotEqualTo(PathFilter.ofGlobs(List.of("Legacy*.java")));
    }

    @Test
    void matchesFullPathOrBareFilename() {
        final var filter = PathFilter.ofGlobs(List.of("**/generated/**", "Legacy*.java"));
        assertThat(filter.excludes(Path.of("src/generated/Foo.java"))).isTrue();
        assertThat(filter.excludes(Path.of("deep/dir/LegacyThing.java"))).isTrue();
        assertThat(filter.excludes(Path.of("src/main/Foo.java"))).isFalse();
    }

    @Test
    void noGlobsExcludesNothing() {
        assertThat(PathFilter.ofGlobs(List.of()).keep(List.of(Path.of("A.java")))).containsExactly(Path.of("A.java"));
    }

    @Test
    void theDefaultExcludedDirectoriesAreTheOutputAndDependencyTrees() {
        assertThat(PathFilter.DEFAULT_EXCLUDED_DIRECTORIES)
                .containsExactlyInAnyOrder(".git", "build", "generated", "node_modules", "out", "target");
    }

    @ParameterizedTest
    @ValueSource(strings = {"build", "target", "out", "node_modules", ".git", "src/main/generated", "proj/build"})
    void aDirectoryNamedLikeAnOutputTreeIsSkipped(final String path) {
        assertThat(PathFilter.isDefaultExcludedDirectory(Path.of(path))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"src", "src/main/java", "buildsrc", "outer", "generated-sources", "build/classes"})
    void anyOtherDirectoryIsSearched(final String path) {
        assertThat(PathFilter.isDefaultExcludedDirectory(Path.of(path))).isFalse();
    }
}
