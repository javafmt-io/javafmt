package io.javafmt.maven;

import io.javafmt.GitRepository;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;

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

    static MavenProject projectInRepositoryWithAnEditedFile(final Path dir) {
        final var repository = GitRepository.init(dir);
        repository.write("src/main/java/Order.java", UNFORMATTED.replace("Foo", "Order"));
        repository.write("src/main/java/Invoice.java", UNFORMATTED.replace("Foo", "Invoice"));
        repository.commitAll("initial");
        repository.write("src/main/java/Invoice.java", UNFORMATTED.replace("Foo", "Invoice").replace("bar", "total"));
        final var project = buildProject(dir.resolve("src/main/java"));
        project.setFile(dir.resolve("pom.xml").toFile());
        return project;
    }

    private MojoTestSupport() {}
}
