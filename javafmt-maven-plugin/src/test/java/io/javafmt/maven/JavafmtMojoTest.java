package io.javafmt.maven;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

class JavafmtMojoTest {

    @Test
    void executeFailsFastUntilImplemented() {
        final var mojo = new JavafmtMojo();
        assertThatThrownBy(mojo::execute)
            .isInstanceOf(MojoExecutionException.class)
            .hasMessage("javafmt-maven-plugin: not yet implemented");
    }
}
