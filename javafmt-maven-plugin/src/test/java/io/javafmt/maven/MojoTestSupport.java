package io.javafmt.maven;

import java.nio.file.Path;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;

/** Shared test scaffolding for the format and check mojo tests. */
final class MojoTestSupport {

    static final String UNFORMATTED = "class   Foo{\n\nvoid bar()  { }\n}\n";

    static MavenProject buildProject(final Path srcDir) {
        final var build = new Build();
        build.setSourceDirectory(srcDir.toAbsolutePath().toString());
        build.setTestSourceDirectory(srcDir.toAbsolutePath().toString() + "-test");
        final var project = new MavenProject();
        project.getModel().setBuild(build);
        return project;
    }

    private MojoTestSupport() {}
}
