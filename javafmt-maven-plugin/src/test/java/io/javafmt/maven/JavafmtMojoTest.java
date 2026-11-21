package io.javafmt.maven;

import io.javafmt.Javafmt;
import io.javafmt.maven.MojoTestSupport.CapturingLog;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;

import static io.javafmt.maven.MojoTestSupport.EDITED_INVOICE;
import static io.javafmt.maven.MojoTestSupport.FORMATTED;
import static io.javafmt.maven.MojoTestSupport.ORDER;
import static io.javafmt.maven.MojoTestSupport.REASSIGNS_A_PARAMETER;
import static io.javafmt.maven.MojoTestSupport.UNFORMATTED;
import static io.javafmt.maven.MojoTestSupport.buildProject;
import static io.javafmt.maven.MojoTestSupport.formatMojo;
import static io.javafmt.maven.MojoTestSupport.projectInMavenLayout;
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
        assertThat(Files.readString(file, StandardCharsets.UTF_8))
                .isEqualTo(Javafmt.format(UNFORMATTED))
                .isNotEqualTo(UNFORMATTED);
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
        final var files = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> srcDir.resolve("F" + i + ".java"))
                .toList();
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
    void formatGoal_withCacheEnabled_skipsUnchangedFilesOnSecondRun(@TempDir final Path dir) throws Exception {
        final var file = Files.writeString(
                Files.createDirectories(dir.resolve("src/main/java")).resolve("Warns.java"), REASSIGNS_A_PARAMETER);
        final var cacheFile = dir.resolve("target/javafmt.cache");
        final var first = new CapturingLog();
        formatMojo(dir, cacheFile, first).execute();
        assertThat(first.messages()).anyMatch(message -> message.contains("is reassigned"));
        assertThat(cacheFile).exists();
        final var second = new CapturingLog();
        formatMojo(dir, cacheFile, second).execute();
        assertThat(second.messages())
                .noneMatch(message -> message.contains("is reassigned"))
                .contains("javafmt: formatted 0 files, 0 warnings");
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(REASSIGNS_A_PARAMETER);
    }

    @Test
    void formatGoal_withCacheDisabled_doesNotCreateCacheFile(@TempDir final Path dir) throws Exception {
        Files.writeString(Files.createDirectories(dir.resolve("src/main/java")).resolve("Warns.java"), REASSIGNS_A_PARAMETER);
        final var mojo = formatMojo(dir);
        mojo.execute();
        final var second = new CapturingLog();
        mojo.setLog(second);
        mojo.execute();
        assertThat(second.messages()).anyMatch(message -> message.contains("is reassigned"));
        assertThat(dir.resolve("target/javafmt.cache")).doesNotExist();
    }

    @Test
    void aCacheEntrySkipsTheSecondRun(@TempDir final Path dir) throws Exception {
        final var file = Files.writeString(
                Files.createDirectories(dir.resolve("src/main/java")).resolve("Warns.java"), REASSIGNS_A_PARAMETER);
        final var cacheFile = dir.resolve("target/javafmt.cache");
        formatMojo(dir, cacheFile, new CapturingLog()).execute();
        assertThat(Files.readAllLines(cacheFile, StandardCharsets.UTF_8)).anySatisfy(line -> assertThat(line).endsWith(" " + file.toAbsolutePath()));
        final var second = new CapturingLog();
        formatMojo(dir, cacheFile, second).execute();
        assertThat(second.messages()).containsExactly("javafmt: formatted 0 files, 0 warnings");
    }

    @Test
    void formatGoalAlsoRewritesTestSources(@TempDir final Path dir) throws Exception {
        final var main = Files.writeString(
                Files.createDirectories(dir.resolve("src/main/java")).resolve("Foo.java"), UNFORMATTED);
        final var test = Files.writeString(
                Files.createDirectories(dir.resolve("src/test/java")).resolve("FooTest.java"), UNFORMATTED);
        formatMojo(dir).execute();
        assertThat(Files.readString(main, StandardCharsets.UTF_8)).isEqualTo(FORMATTED);
        assertThat(Files.readString(test, StandardCharsets.UTF_8)).isEqualTo(FORMATTED);
    }

    @Test
    void formatGoalSkipsSourceDirectoriesTheBuildLeavesUnset(@TempDir final Path dir) throws Exception {
        final var main = Files.writeString(
                Files.createDirectories(dir.resolve("src/main/java")).resolve("Foo.java"), UNFORMATTED);
        final var test = Files.writeString(
                Files.createDirectories(dir.resolve("src/test/java")).resolve("FooTest.java"), UNFORMATTED);
        final var project = projectInMavenLayout(dir);
        project.getBuild().setSourceDirectory(null);
        formatMojo(project).execute();
        assertThat(Files.readString(main)).isEqualTo(UNFORMATTED);
        assertThat(Files.readString(test)).isEqualTo(FORMATTED);
        project.getBuild().setTestSourceDirectory(null);
        assertThatNoException().isThrownBy(formatMojo(project)::execute);
    }

    @Test
    void theSummaryLineCountsChangedFiles(@TempDir final Path dir) throws Exception {
        final var src = Files.createDirectories(dir.resolve("src/main/java"));
        Files.writeString(src.resolve("Foo.java"), UNFORMATTED);
        Files.writeString(src.resolve("Done.java"), FORMATTED.replace("Foo", "Done"));
        final var log = new CapturingLog();
        final var mojo = formatMojo(dir);
        mojo.setLog(log);
        mojo.execute();
        assertThat(log.messages()).contains("javafmt: formatted 1 files, 0 warnings");
    }

    @Test
    void anUnwritableFileFailsWithTheIoCause(@TempDir final Path dir) throws Exception {
        final var file = Files.writeString(
                Files.createDirectories(dir.resolve("src/main/java")).resolve("Foo.java"), UNFORMATTED);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
        assertThatThrownBy(formatMojo(dir)::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessage("javafmt: I/O error: Failed to write " + file.toAbsolutePath())
                .hasCauseInstanceOf(AccessDeniedException.class);
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(UNFORMATTED);
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
        assertThat(Files.readString(dir.resolve("src/main/java/Invoice.java"), StandardCharsets.UTF_8))
                .isEqualTo(Javafmt.format(EDITED_INVOICE));
        assertThat(Files.readString(dir.resolve("src/main/java/Order.java"), StandardCharsets.UTF_8))
                .isEqualTo(ORDER);
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
        assertThat(Files.readString(normal, StandardCharsets.UTF_8))
                .isEqualTo(Javafmt.format(UNFORMATTED))
                .isNotEqualTo(UNFORMATTED);
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
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessage("javafmt: I/O error: Failed to read " + unreadable)
                .hasCauseInstanceOf(CharacterCodingException.class);
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
