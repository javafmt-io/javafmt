package io.github.jschneidereit.grind.maven;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

class GrindMojoTest {

    @Test
    void executeFailsFastUntilImplemented() {
        final var mojo = new GrindMojo();
        assertThatThrownBy(mojo::execute)
            .isInstanceOf(MojoExecutionException.class)
            .hasMessage("grind-maven-plugin: not yet implemented");
    }
}
