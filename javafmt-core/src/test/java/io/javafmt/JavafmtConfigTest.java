package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavafmtConfigTest {

    @Test
    void defaults_excludesIsEmpty() {
        assertThat(new JavafmtConfig().excludes()).isEmpty();
    }

    @Test
    void excludes_roundTripsGlobList() {
        final var globs = List.of("**/input.java", "**/expected.java", "src/test/resources/test-fixtures/**");
        final var config = JavafmtConfig.defaults().withExcludes(globs);
        assertThat(config.excludes()).containsExactlyElementsOf(globs);
    }

    @Test
    void excludes_isUnmodifiable() {
        final var config = JavafmtConfig.defaults().withExcludes(List.of("**/foo.java"));
        assertThat(config.excludes()).isUnmodifiable();
    }

    @Test
    void excludes_defensivelyCopiesInput() {
        final var globs = new ArrayList<>(List.of("**/input.java"));
        final var config = JavafmtConfig.defaults().withExcludes(globs);
        globs.add("**/added-after.java");
        assertThat(config.excludes()).containsExactly("**/input.java");
    }
}
