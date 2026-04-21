package io.github.jschneidereit.grind.maven;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "format")
@Slf4j
public final class GrindMojo extends AbstractMojo {

    @Override
    public void execute() {
        log.info("grind-maven-plugin: format goal (stub)");
    }
}
