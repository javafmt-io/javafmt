package io.javafmt.maven;

import io.javafmt.GitRepository;
import io.javafmt.Javafmt;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared test scaffolding for the format and check mojo tests. */
final class MojoTestSupport {

    static final String UNFORMATTED = "class   Foo{\n\nvoid bar()  { }\n}\n";

    static final String FORMATTED = Javafmt.format(UNFORMATTED);

    static final String ORDER = UNFORMATTED.replace("Foo", "Order");

    static final String EDITED_INVOICE = UNFORMATTED.replace("Foo", "Invoice").replace("bar", "total");

    static final String REASSIGNS_A_PARAMETER = Javafmt.format("class Warns {\n    void f(int x) {\n        x = 1;\n    }\n}\n");

    static MavenProject buildProject(final Path srcDir) {
        return buildProject(srcDir, srcDir, srcDir.resolveSibling(srcDir.getFileName() + "-test"));
    }

    static MavenProject buildProject(final Path root, final Path srcDir, final Path testDir) {
        final var build = new Build();
        build.setDirectory(root.toAbsolutePath().resolve("target").toString());
        build.setSourceDirectory(srcDir.toAbsolutePath().toString());
        build.setTestSourceDirectory(testDir.toAbsolutePath().toString());
        final var project = new MavenProject();
        project.getModel().setBuild(build);
        return project;
    }

    static MavenProject projectInMavenLayout(final Path dir) {
        return buildProject(dir, dir.resolve("src/main/java"), dir.resolve("src/test/java"));
    }

    static JavafmtMojo formatMojo(final Path dir) {
        return withLog(new JavafmtMojo(projectInMavenLayout(dir), List.of(), Optional.empty()), new CapturingLog());
    }

    static JavafmtMojo formatMojo(final Path dir, final Path cacheFile, final CapturingLog log) {
        return withLog(new JavafmtMojo(projectInMavenLayout(dir), List.of(), Optional.of(cacheFile)), log);
    }

    static JavafmtCheckMojo checkMojo(final Path dir) {
        return new JavafmtCheckMojo(projectInMavenLayout(dir), List.of(), Optional.empty());
    }

    static JavafmtCheckMojo checkMojo(final Path dir, final Path cacheFile) {
        return new JavafmtCheckMojo(projectInMavenLayout(dir), List.of(), Optional.of(cacheFile));
    }

    private static JavafmtMojo withLog(final JavafmtMojo mojo, final CapturingLog log) {
        mojo.setLog(log);
        return mojo;
    }

    static MavenProject projectInRepositoryWithAnEditedFile(final Path dir) {
        final var repository = GitRepository.init(dir);
        repository.write("src/main/java/Order.java", ORDER);
        repository.write("src/main/java/Invoice.java", UNFORMATTED.replace("Foo", "Invoice"));
        repository.commitAll("initial");
        repository.write("src/main/java/Invoice.java", EDITED_INVOICE);
        final var project = buildProject(dir.resolve("src/main/java"));
        project.setFile(dir.resolve("pom.xml").toFile());
        return project;
    }

    static final class CapturingLog extends SystemStreamLog {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void info(final CharSequence content) {
            messages.add(content.toString());
        }

        @Override
        public void warn(final CharSequence content) {
            messages.add(content.toString());
        }

        List<String> messages() {
            return List.copyOf(messages);
        }
    }

    private MojoTestSupport() {}
}
