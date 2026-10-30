package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class JavafmtConfigTest {

    @Test
    void defaults_parseOnly() {
        assertThat(JavafmtConfig.defaults().analysis()).isEqualTo(new JavafmtConfig.Analysis.ParseOnly());
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
