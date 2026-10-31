package io.javafmt;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class JavafmtConfigTest {
    @Test
    void defaults_parseOnly() {
        assertThat(JavafmtConfig.defaults().analysis()).isEqualTo(new JavafmtConfig.Analysis.ParseOnly());
    }

    @Test
    void defaults_targetTheLatestSupportedRelease() {
        assertThat(JavafmtConfig.defaults().release()).isEqualTo(25);
    }

    @Test
    void release_isKeptByTheOtherWithers() {
        final var config = JavafmtConfig.defaults().withRelease(21);
        assertThat(config.release()).isEqualTo(21);
        assertThat(config.withLint(false).withVerifyOutput(false).withAnalysis(new JavafmtConfig.Analysis.ParseOnly()).release()).isEqualTo(21);
    }

    @Test
    void analysis_carriesTheClasspath() {
        final var config = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        assertThat(config.analysis()).isEqualTo(new JavafmtConfig.Analysis.Attributed("/lib/a.jar"));
        assertThat(config.withLint(false).analysis()).isEqualTo(config.analysis());
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
