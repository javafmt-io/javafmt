package io.github.jschneidereit.grind.maven;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "format", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
@Slf4j
public final class GrindMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        throw new MojoExecutionException("grind-maven-plugin: not yet implemented");
    }
}
