package io.javafmt.maven;

import io.javafmt.Javafmt;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;

import static io.javafmt.maven.MojoTestSupport.UNFORMATTED;
import static io.javafmt.maven.MojoTestSupport.buildProject;
import static io.javafmt.maven.MojoTestSupport.projectInRepositoryWithAnEditedFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavafmtMojoTest {

    @Test
    void formatGoalRewritesUnformattedFile(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, UNFORMATTED, StandardCharsets.UTF_8);
        final var mojo = new JavafmtMojo(buildProject(srcDir), List.of(), Optional.empty());
        mojo.execute();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(Javafmt.format(UNFORMATTED)).isNotEqualTo(UNFORMATTED);
    }

    @Test
    void formatGoalLeavesFormattedFileUnchanged(@TempDir final Path srcDir) throws Exception {
        final var formatted = Javafmt.format(UNFORMATTED);
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, formatted, StandardCharsets.UTF_8);
        final var mojo = new JavafmtMojo(buildProject(srcDir), List.of(), Optional.empty());
        mojo.execute();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(formatted);
    }

    @Test
    void formatGoalSkipsFileWithParseError(@TempDir final Path srcDir) throws Exception {
        final var badContent = "this is not valid java source code";
        final var file = srcDir.resolve("Bad.java");
        Files.writeString(file, badContent, StandardCharsets.UTF_8);
        final var mojo = new JavafmtMojo(buildProject(srcDir), List.of(), Optional.empty());
        assertThatNoException().isThrownBy(mojo::execute);
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(badContent);
    }

    @Test
    void formatGoal_formatsMultipleFilesInParallel(@TempDir final Path srcDir) throws Exception {
        final var files = java.util.stream.IntStream.range(0, 8).mapToObj(i -> srcDir.resolve("F" + i + ".java")).toList();
        for (final var file : files) {
            Files.writeString(file, UNFORMATTED, StandardCharsets.UTF_8);
        }
        final var mojo = new JavafmtMojo(buildProject(srcDir), List.of(), Optional.empty());
        mojo.execute();
        final var formatted = Javafmt.format(UNFORMATTED);
        for (final var file : files) {
            assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(formatted);
        }
    }

    @Test
    void formatGoal_withCacheEnabled_skipsUnchangedFilesOnSecondRun(@TempDir final Path srcDir, @TempDir final Path cacheDir) throws Exception {
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, UNFORMATTED, StandardCharsets.UTF_8);
        final var cacheFile = cacheDir.resolve("javafmt.cache");
        final var mojo = new JavafmtMojo(buildProject(srcDir), List.of(), Optional.of(cacheFile));
        mojo.execute();
        final var afterFirst = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(afterFirst).isEqualTo(Javafmt.format(UNFORMATTED));
        assertThat(Files.exists(cacheFile)).isTrue();
        final var mtimeAfterFirst = Files.getLastModifiedTime(file);
        final var mojo2 = new JavafmtMojo(buildProject(srcDir), List.of(), Optional.of(cacheFile));
        mojo2.execute();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(afterFirst);
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(mtimeAfterFirst);
    }

    @Test
    void formatGoal_withCacheDisabled_doesNotCreateCacheFile(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, UNFORMATTED, StandardCharsets.UTF_8);
        final var mojo = new JavafmtMojo(buildProject(srcDir), List.of(), Optional.empty());
        mojo.execute();
        try (final var list = Files.list(srcDir)) {
            assertThat(list.map(Path::getFileName).map(Path::toString)).noneMatch(name -> name.contains("javafmt.cache"));
        }
    }

    @Test
    void formatGoalKeepsThePermissionsOfTheFileItRewrites(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, UNFORMATTED, StandardCharsets.UTF_8);
        final var permissions = PosixFilePermissions.fromString("rw-r-----");
        Files.setPosixFilePermissions(file, permissions);
        new JavafmtMojo(buildProject(srcDir), List.of(), Optional.empty()).execute();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(Javafmt.format(UNFORMATTED));
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(permissions);
        try (final var entries = Files.list(srcDir)) {
            assertThat(entries).containsExactly(file);
        }
    }

    @Test
    void formatGoalWithRatchetFromOnlyRewritesTheFilesChangedSinceTheRef(@TempDir final Path dir) throws Exception {
        new JavafmtMojo(projectInRepositoryWithAnEditedFile(dir), List.of(), Optional.empty(), Optional.of("main")).execute();
        final var edited = UNFORMATTED.replace("Foo", "Invoice").replace("bar", "total");
        assertThat(Files.readString(dir.resolve("src/main/java/Invoice.java"), StandardCharsets.UTF_8)).isEqualTo(Javafmt.format(edited));
        assertThat(Files.readString(dir.resolve("src/main/java/Order.java"), StandardCharsets.UTF_8)).isEqualTo(UNFORMATTED.replace("Foo", "Order"));
    }

    @Test
    void formatGoalSkipsExcludedFiles(@TempDir final Path srcDir) throws Exception {
        final var fixture = srcDir.resolve("test-fixtures/input.java");
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, UNFORMATTED, StandardCharsets.UTF_8);
        final var normal = srcDir.resolve("Normal.java");
        Files.writeString(normal, UNFORMATTED, StandardCharsets.UTF_8);
        final var mojo = new JavafmtMojo(buildProject(srcDir), List.of("**/input.java"), Optional.empty());
        mojo.execute();
        assertThat(Files.readString(fixture, StandardCharsets.UTF_8)).isEqualTo(UNFORMATTED);
        assertThat(Files.readString(normal, StandardCharsets.UTF_8)).isEqualTo(Javafmt.format(UNFORMATTED)).isNotEqualTo(UNFORMATTED);
    }

    @Test
    void formatGoalSkipsGeneratedSourcesUnderTheSourceRoot(@TempDir final Path srcDir) throws Exception {
        final var generated = srcDir.resolve("generated/Gen.java");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, UNFORMATTED, StandardCharsets.UTF_8);
        final var normal = srcDir.resolve("Normal.java");
        Files.writeString(normal, UNFORMATTED, StandardCharsets.UTF_8);
        new JavafmtMojo(buildProject(srcDir), List.of(), Optional.empty()).execute();
        assertThat(Files.readString(generated, StandardCharsets.UTF_8)).isEqualTo(UNFORMATTED);
        assertThat(Files.readString(normal, StandardCharsets.UTF_8)).isEqualTo(Javafmt.format(UNFORMATTED));
    }

    @Test
    void anUnreadableFileFailsTheGoalWithoutWritingItsNeighbours(@TempDir final Path srcDir) throws Exception {
        final var unreadable = srcDir.resolve("Latin1.java");
        Files.write(unreadable, new byte[]{'c', 'l', 'a', 's', 's', ' ', (byte) 0xE9, ' ', '{', '}', '\n'});
        final var normal = srcDir.resolve("Normal.java");
        Files.writeString(normal, UNFORMATTED, StandardCharsets.UTF_8);
        final var mojo = new JavafmtMojo(buildProject(srcDir), List.of(), Optional.empty());
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class).hasMessage("javafmt: I/O error: Failed to read " + unreadable);
        assertThat(Files.readString(normal, StandardCharsets.UTF_8)).isEqualTo(UNFORMATTED);
    }

    @Test
    void formatGoalSkipsExcludedFilesByFullPathGlob(@TempDir final Path srcDir) throws Exception {
        final var fixture = srcDir.resolve("fixtures/SomeFile.java");
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, UNFORMATTED, StandardCharsets.UTF_8);
        final var normal = srcDir.resolve("Normal.java");
        Files.writeString(normal, UNFORMATTED, StandardCharsets.UTF_8);
        final var mojo = new JavafmtMojo(buildProject(srcDir), List.of("**/fixtures/**"), Optional.empty());
        mojo.execute();
        assertThat(Files.readString(fixture, StandardCharsets.UTF_8)).isEqualTo(UNFORMATTED);
        assertThat(Files.readString(normal, StandardCharsets.UTF_8)).isNotEqualTo(UNFORMATTED);
    }
}
