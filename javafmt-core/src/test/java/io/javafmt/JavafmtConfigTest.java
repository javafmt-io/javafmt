package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

    @Test
    void defaults_parseOnly() {
        assertThat(JavafmtConfig.defaults().analysis()).isEqualTo(new JavafmtConfig.Analysis.ParseOnly());
    }

    @Test
    void analysis_carriesTheClasspath() {
        final var config = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        assertThat(config.analysis()).isEqualTo(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        assertThat(config.withLint(false).analysis()).isEqualTo(config.analysis());
        assertThat(config.withExcludes(List.of("**/x.java")).analysis()).isEqualTo(config.analysis());
        assertThat(config.withVerifyOutput(false).analysis()).isEqualTo(config.analysis());
    }

    @Test
    void analysis_survivesSerialization() throws IOException, ClassNotFoundException {
        final var config = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        final var bytes = new ByteArrayOutputStream();
        try (final var out = new ObjectOutputStream(bytes)) {
            out.writeObject(config);
        }
        try (final var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(in.readObject()).isEqualTo(config);
        }
    }
}
