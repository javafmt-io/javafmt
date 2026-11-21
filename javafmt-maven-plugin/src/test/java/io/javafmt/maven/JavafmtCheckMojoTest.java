package io.javafmt.maven;

import io.javafmt.FileResult;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.cache.CacheKey;
import io.javafmt.cache.FileFormatCache;
import io.javafmt.maven.MojoTestSupport.CapturingLog;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static io.javafmt.maven.MojoTestSupport.FORMATTED;
import static io.javafmt.maven.MojoTestSupport.REASSIGNS_A_PARAMETER;
import static io.javafmt.maven.MojoTestSupport.UNFORMATTED;
import static io.javafmt.maven.MojoTestSupport.buildProject;
import static io.javafmt.maven.MojoTestSupport.checkMojo;
import static io.javafmt.maven.MojoTestSupport.formatMojo;
import static io.javafmt.maven.MojoTestSupport.projectInRepositoryWithAnEditedFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavafmtCheckMojoTest {

    @Test
    void emptyFileIsProcessedNotMistakenForCacheHit(@TempDir final Path dir) throws Exception {
        final var src = Files.createDirectories(dir.resolve("src/main/java"));
        Files.writeString(src.resolve("Empty.java"), "");
        Files.writeString(src.resolve("Unformatted.java"), UNFORMATTED);
        final var mojo = new JavafmtCheckMojo(buildProject(dir), List.of(), Optional.empty());
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("1 file(s) not formatted")
                .hasMessageContaining("Unformatted.java");
    }

    @Test
    void emptyFileIsRecordedInCacheNotSilentlySkipped(@TempDir final Path dir, @TempDir final Path cacheDir) throws Exception {
        final var src = Files.createDirectories(dir.resolve("src/main/java"));
        final var empty = src.resolve("Empty.java");
        Files.writeString(empty, "");
        final var cacheFile = cacheDir.resolve("javafmt.cache");
        final var mojo = new JavafmtCheckMojo(buildProject(dir), List.of(), Optional.of(cacheFile));
        assertThatNoException().isThrownBy(mojo::execute);
        // File format is "# javafmt cache v<n> <version>" header, then one "<sha256-hex> <path>"
        // line per entry (io.javafmt.cache.FileFormatCache). A genuinely-empty file that was
        // never a cache hit must still get its own entry — a prior bug filtered it out of the
        // check pipeline entirely (mistaking "empty content" for the cache-hit sentinel "") and
        // it was never recorded.
        final var lines = Files.readAllLines(cacheFile, StandardCharsets.UTF_8);
        assertThat(lines).anySatisfy(line -> assertThat(line).endsWith(" " + empty.toAbsolutePath()));
    }

    @Test
    void directoryNamedLikeJavaFileIsIgnored(@TempDir final Path dir) throws Exception {
        final var src = Files.createDirectories(dir.resolve("src/main/java"));
        Files.createDirectories(src.resolve("Trap.java"));
        Files.writeString(src.resolve("A.java"), FORMATTED);
        new JavafmtCheckMojo(buildProject(dir), List.of(), Optional.empty()).execute(); // must not throw
    }

    @Test
    void checkGoalPassesWhenAllFilesFormatted(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, Javafmt.format(UNFORMATTED), StandardCharsets.UTF_8);
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir), List.of(), Optional.empty());
        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void checkGoalFailsWithUnformattedFilesNamed(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Unformatted.java");
        Files.writeString(file, UNFORMATTED, StandardCharsets.UTF_8);
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir), List.of(), Optional.empty());
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("javafmt check failed: 1 file(s) not formatted")
                .hasMessageContaining(file.toString());
    }

    @Test
    void checkGoalSkipsParseErrorFilesWithoutFailing(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Bad.java");
        Files.writeString(file, "this is not valid java source code", StandardCharsets.UTF_8);
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir), List.of(), Optional.empty());
        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void checkGoal_withCacheEnabled_doesNotFailOnUpToDateFile(@TempDir final Path srcDir, @TempDir final Path cacheDir) throws Exception {
        final var formatted = Javafmt.format(UNFORMATTED);
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, formatted, StandardCharsets.UTF_8);
        final var cacheFile = cacheDir.resolve("javafmt.cache");
        final var first = new JavafmtCheckMojo(buildProject(srcDir), List.of(), Optional.of(cacheFile));
        assertThatNoException().isThrownBy(first::execute);
        final var second = new JavafmtCheckMojo(buildProject(srcDir), List.of(), Optional.of(cacheFile));
        assertThatNoException().isThrownBy(second::execute);
    }

    @Test
    void checkGoal_withCacheEnabled_stillFailsOnUnformattedFile(@TempDir final Path srcDir, @TempDir final Path cacheDir) throws Exception {
        final var file = srcDir.resolve("Unformatted.java");
        Files.writeString(file, UNFORMATTED, StandardCharsets.UTF_8);
        final var cacheFile = cacheDir.resolve("javafmt.cache");
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir), List.of(), Optional.of(cacheFile));
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(file.toString());
    }

    @Test
    void aSecondCheckOnTheSameCacheStillFails(@TempDir final Path dir) throws Exception {
        final var file = Files.writeString(
                Files.createDirectories(dir.resolve("src/main/java")).resolve("Unformatted.java"), UNFORMATTED);
        final var cacheFile = dir.resolve("target/javafmt.cache");
        assertThatThrownBy(checkMojo(dir, cacheFile)::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(file.toString());
        assertThatThrownBy(checkMojo(dir, cacheFile)::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("1 file(s) not formatted")
                .hasMessageContaining(file.toString());
    }

    @Test
    void aFailedCheckCachesOnlyTheFormattedFiles(@TempDir final Path dir) {
        final var unformatted = new FileResult.Formatted(dir.resolve("Unformatted.java"), UNFORMATTED, Javafmt.formatWithResult(UNFORMATTED));
        final var formatted = new FileResult.Formatted(dir.resolve("Foo.java"), FORMATTED, Javafmt.formatWithResult(FORMATTED));
        final var cache = FileFormatCache.load(dir.resolve("javafmt.cache"), CacheKey.of(JavafmtConfig.defaults()));
        assertThatThrownBy(() -> new JavafmtCheckMojo().handle(List.of(unformatted, formatted), cache))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("1 file(s) not formatted");
        assertThat(cache.isUpToDate(unformatted.path(), UNFORMATTED)).isFalse();
        assertThat(cache.isUpToDate(formatted.path(), FORMATTED)).isTrue();
    }

    @Test
    void checkGoalLogsWarningsLikeTheFormatGoal(@TempDir final Path dir) throws Exception {
        Files.writeString(Files.createDirectories(dir.resolve("src/main/java")).resolve("Warns.java"), REASSIGNS_A_PARAMETER);
        final var formatLog = new CapturingLog();
        final var format = formatMojo(dir);
        format.setLog(formatLog);
        format.execute();
        final var checkLog = new CapturingLog();
        final var check = checkMojo(dir);
        check.setLog(checkLog);
        check.execute();
        final var warnings = formatLog.messages().stream()
                .filter(message -> message.contains("is reassigned"))
                .toList();
        assertThat(warnings).hasSize(1);
        assertThat(checkLog.messages()).containsExactlyElementsOf(warnings);
    }

    @Test
    void checkGoalWithRatchetFromOnlyChecksTheFilesChangedSinceTheRef(@TempDir final Path dir) {
        final var mojo = new JavafmtCheckMojo(projectInRepositoryWithAnEditedFile(dir), List.of(), Optional.empty(), Optional.of("main"));
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("1 file(s) not formatted")
                .hasMessageContaining("Invoice.java")
                .satisfies(t -> assertThat(t.getMessage()).doesNotContain("Order.java"));
    }

    @Test
    void checkGoalWithAnUnknownRatchetRefFailsAndNamesTheRef(@TempDir final Path dir) {
        final var mojo = new JavafmtCheckMojo(projectInRepositoryWithAnEditedFile(dir), List.of(), Optional.empty(), Optional.of("no-such-branch"));
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessage("javafmt: unknown git ref: no-such-branch");
    }

    @Test
    void checkGoalIgnoresExcludedUnformattedFiles(@TempDir final Path srcDir) throws Exception {
        final var fixture = srcDir.resolve("test-fixtures/input.java");
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, UNFORMATTED, StandardCharsets.UTF_8);
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir), List.of("**/input.java"), Optional.empty());
        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void checkGoalStillFailsForUnformattedFilesOutsideExcludes(@TempDir final Path srcDir) throws Exception {
        final var fixture = srcDir.resolve("test-fixtures/input.java");
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, UNFORMATTED, StandardCharsets.UTF_8);
        final var normal = srcDir.resolve("Normal.java");
        Files.writeString(normal, UNFORMATTED, StandardCharsets.UTF_8);
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir), List.of("**/input.java"), Optional.empty());
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(normal.toString())
                .satisfies(t -> assertThat(t.getMessage()).doesNotContain(fixture.toString()));
    }
}
