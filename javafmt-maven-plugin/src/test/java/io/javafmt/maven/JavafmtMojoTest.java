package io.javafmt.maven;

import io.javafmt.Javafmt;
import io.javafmt.maven.MojoTestSupport.CapturingLog;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.CharacterCodingException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static io.javafmt.maven.MojoTestSupport.EDITED_INVOICE;
import static io.javafmt.maven.MojoTestSupport.FORMATTED;
import static io.javafmt.maven.MojoTestSupport.ORDER;
import static io.javafmt.maven.MojoTestSupport.REASSIGNS_A_PARAMETER;
import static io.javafmt.maven.MojoTestSupport.UNFORMATTED;
import static io.javafmt.maven.MojoTestSupport.formatMojo;
import static io.javafmt.maven.MojoTestSupport.projectInMavenLayout;
import static io.javafmt.maven.MojoTestSupport.projectInRepositoryWithAnEditedFile;
import static io.javafmt.maven.MojoTestSupport.sourceDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavafmtMojoTest {

    @Test
    void formatGoalRewritesUnformattedFile(@TempDir final Path dir) throws Exception {
        final var file = Files.writeString(sourceDirectory(dir).resolve("Foo.java"), UNFORMATTED);
        formatMojo(dir).execute();
        assertThat(Files.readString(file))
                .isEqualTo(FORMATTED)
                .isNotEqualTo(UNFORMATTED);
    }

    @Test
    void formatGoalLeavesFormattedFileUnchanged(@TempDir final Path dir) throws Exception {
        final var file = Files.writeString(sourceDirectory(dir).resolve("Foo.java"), FORMATTED);
        formatMojo(dir).execute();
        assertThat(Files.readString(file)).isEqualTo(FORMATTED);
    }

    @Test
    void formatGoalSkipsFileWithParseError(@TempDir final Path dir) throws Exception {
        final var badContent = "this is not valid java source code";
        final var file = Files.writeString(sourceDirectory(dir).resolve("Bad.java"), badContent);
        assertThatNoException().isThrownBy(formatMojo(dir)::execute);
        assertThat(Files.readString(file)).isEqualTo(badContent);
    }

    @Test
    void formatGoalFormatsMultipleFilesInParallel(@TempDir final Path dir) throws Exception {
        final var src = sourceDirectory(dir);
        final var files = IntStream.range(0, 8)
                .mapToObj(i -> src.resolve("F" + i + ".java"))
                .toList();
        for (final var file : files) {
            Files.writeString(file, UNFORMATTED);
        }
        formatMojo(dir).execute();
        assertThat(files).allSatisfy(file -> assertThat(Files.readString(file)).isEqualTo(FORMATTED));
    }

    @Test
    void formatGoalWithACacheSkipsUnchangedFilesOnTheSecondRun(@TempDir final Path dir) throws Exception {
        final var file = Files.writeString(sourceDirectory(dir).resolve("Warns.java"), REASSIGNS_A_PARAMETER);
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
        assertThat(Files.readString(file)).isEqualTo(REASSIGNS_A_PARAMETER);
    }

    @Test
    void formatGoalWithoutACacheCreatesNoCacheFile(@TempDir final Path dir) throws Exception {
        Files.writeString(sourceDirectory(dir).resolve("Warns.java"), REASSIGNS_A_PARAMETER);
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
        final var file = Files.writeString(sourceDirectory(dir).resolve("Warns.java"), REASSIGNS_A_PARAMETER);
        final var cacheFile = dir.resolve("target/javafmt.cache");
        formatMojo(dir, cacheFile, new CapturingLog()).execute();
        assertThat(Files.readAllLines(cacheFile)).anySatisfy(line -> assertThat(line).endsWith(" " + file.toAbsolutePath()));
        final var second = new CapturingLog();
        formatMojo(dir, cacheFile, second).execute();
        assertThat(second.messages()).containsExactly("javafmt: formatted 0 files, 0 warnings");
    }

    @Test
    void aRelativeCacheFileResolvesAgainstTheModuleDirectory(@TempDir final Path dir) throws Exception {
        Files.writeString(sourceDirectory(dir).resolve("Foo.java"), FORMATTED);
        final var cacheFile = Path.of("build/javafmt-relative.cache");
        formatMojo(dir, cacheFile, new CapturingLog()).execute();
        assertThat(dir.resolve(cacheFile)).exists();
    }

    @Test
    void formatGoalAlsoRewritesTestSources(@TempDir final Path dir) throws Exception {
        final var main = Files.writeString(sourceDirectory(dir).resolve("Foo.java"), UNFORMATTED);
        final var test = Files.writeString(
                Files.createDirectories(dir.resolve("src/test/java")).resolve("FooTest.java"), UNFORMATTED);
        formatMojo(dir).execute();
        assertThat(Files.readString(main)).isEqualTo(FORMATTED);
        assertThat(Files.readString(test)).isEqualTo(FORMATTED);
    }

    @Test
    void formatGoalSkipsSourceDirectoriesTheBuildLeavesUnset(@TempDir final Path dir) throws Exception {
        final var main = Files.writeString(sourceDirectory(dir).resolve("Foo.java"), UNFORMATTED);
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
        final var src = sourceDirectory(dir);
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
        final var file = Files.writeString(sourceDirectory(dir).resolve("Foo.java"), UNFORMATTED);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
        assertThatThrownBy(formatMojo(dir)::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessage("javafmt: I/O error: Failed to write " + file.toAbsolutePath())
                .hasCauseInstanceOf(AccessDeniedException.class);
        assertThat(Files.readString(file)).isEqualTo(UNFORMATTED);
    }

    @Test
    void formatGoalKeepsThePermissionsOfTheFileItRewrites(@TempDir final Path dir) throws Exception {
        final var file = Files.writeString(sourceDirectory(dir).resolve("Foo.java"), UNFORMATTED);
        final var permissions = PosixFilePermissions.fromString("rw-r-----");
        Files.setPosixFilePermissions(file, permissions);
        formatMojo(dir).execute();
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(permissions);
        assertThat(Files.readString(file)).isEqualTo(FORMATTED);
        try (final var siblings = Files.list(file.getParent())) {
            assertThat(siblings.toList()).containsExactly(file);
        }
    }

    @Test
    void formatGoalWithRatchetFromOnlyRewritesTheFilesChangedSinceTheRef(@TempDir final Path dir) throws Exception {
        new JavafmtMojo(projectInRepositoryWithAnEditedFile(dir), List.of(), Optional.empty(), Optional.of("main")).execute();
        assertThat(Files.readString(dir.resolve("src/main/java/Invoice.java"))).isEqualTo(Javafmt.format(EDITED_INVOICE));
        assertThat(Files.readString(dir.resolve("src/main/java/Order.java"))).isEqualTo(ORDER);
    }

    @ParameterizedTest
    @CsvSource({"test-fixtures/input.java, **/input.java", "fixtures/SomeFile.java, **/fixtures/**"})
    void formatGoalSkipsExcludedFiles(final String excludedFile, final String glob, @TempDir final Path dir) throws Exception {
        final var src = sourceDirectory(dir);
        final var excluded = src.resolve(excludedFile);
        Files.createDirectories(excluded.getParent());
        Files.writeString(excluded, UNFORMATTED);
        final var normal = Files.writeString(src.resolve("Normal.java"), UNFORMATTED);
        new JavafmtMojo(projectInMavenLayout(dir), List.of(glob), Optional.empty(), Optional.empty()).execute();
        assertThat(Files.readString(excluded)).isEqualTo(UNFORMATTED);
        assertThat(Files.readString(normal)).isEqualTo(FORMATTED);
    }

    @Test
    void formatGoalSkipsGeneratedSourcesUnderTheSourceRoot(@TempDir final Path dir) throws Exception {
        final var src = sourceDirectory(dir);
        final var generated = Files.createDirectories(src.resolve("generated")).resolve("Gen.java");
        Files.writeString(generated, UNFORMATTED);
        final var normal = Files.writeString(src.resolve("Normal.java"), UNFORMATTED);
        formatMojo(dir).execute();
        assertThat(Files.readString(generated)).isEqualTo(UNFORMATTED);
        assertThat(Files.readString(normal)).isEqualTo(FORMATTED);
    }

    @Test
    void anUnreadableFileFailsTheGoalWithoutWritingItsNeighbours(@TempDir final Path dir) throws Exception {
        final var src = sourceDirectory(dir);
        final var unreadable = src.resolve("Latin1.java");
        Files.write(unreadable, new byte[]{'c', 'l', 'a', 's', 's', ' ', (byte) 0xE9, ' ', '{', '}', '\n'});
        final var normal = Files.writeString(src.resolve("Normal.java"), UNFORMATTED);
        assertThatThrownBy(formatMojo(dir)::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessage("javafmt: I/O error: Failed to read " + unreadable)
                .hasCauseInstanceOf(CharacterCodingException.class);
        assertThat(Files.readString(normal)).isEqualTo(UNFORMATTED);
    }
}
