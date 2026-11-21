package io.javafmt.maven;

import io.javafmt.FileResult;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.cache.CacheKey;
import io.javafmt.cache.FileFormatCache;
import io.javafmt.maven.MojoTestSupport.CapturingLog;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.javafmt.maven.MojoTestSupport.FORMATTED;
import static io.javafmt.maven.MojoTestSupport.REASSIGNS_A_PARAMETER;
import static io.javafmt.maven.MojoTestSupport.UNFORMATTED;
import static io.javafmt.maven.MojoTestSupport.checkMojo;
import static io.javafmt.maven.MojoTestSupport.formatMojo;
import static io.javafmt.maven.MojoTestSupport.projectInMavenLayout;
import static io.javafmt.maven.MojoTestSupport.projectInRepositoryWithAnEditedFile;
import static io.javafmt.maven.MojoTestSupport.sourceDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavafmtCheckMojoTest {

    private static Stream<Named<Function<Path, JavafmtCheckMojo>>> checkGoals() {
        return Stream.of(
                Named.of("without a cache", MojoTestSupport::checkMojo),
                Named.of("with a cache", dir -> checkMojo(dir, dir.resolve("target/javafmt.cache"))));
    }

    @Test
    void anEmptyFileIsNotReportedAsUnformatted(@TempDir final Path dir) throws Exception {
        final var src = sourceDirectory(dir);
        Files.writeString(src.resolve("Empty.java"), "");
        Files.writeString(src.resolve("Unformatted.java"), UNFORMATTED);
        assertThatThrownBy(checkMojo(dir)::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("1 file(s) not formatted")
                .hasMessageContaining("Unformatted.java");
    }

    @Test
    void emptyFileIsRecordedInCacheNotSilentlySkipped(@TempDir final Path dir, @TempDir final Path cacheDir) throws Exception {
        final var empty = Files.writeString(sourceDirectory(dir).resolve("Empty.java"), "");
        final var cacheFile = cacheDir.resolve("javafmt.cache");
        assertThatNoException().isThrownBy(checkMojo(dir, cacheFile)::execute);
        // File format is "# javafmt cache v<n> <version>" header, then one "<sha256-hex> <path>"
        // line per entry (io.javafmt.cache.FileFormatCache). A genuinely-empty file that was
        // never a cache hit must still get its own entry — a prior bug filtered it out of the
        // check pipeline entirely (mistaking "empty content" for the cache-hit sentinel "") and
        // it was never recorded.
        assertThat(Files.readAllLines(cacheFile)).anySatisfy(line -> assertThat(line).endsWith(" " + empty.toAbsolutePath()));
    }

    @Test
    void directoryNamedLikeJavaFileIsIgnored(@TempDir final Path dir) throws Exception {
        final var src = sourceDirectory(dir);
        Files.createDirectories(src.resolve("Trap.java"));
        Files.writeString(src.resolve("A.java"), FORMATTED);
        assertThatNoException().isThrownBy(checkMojo(dir)::execute);
    }

    @ParameterizedTest
    @MethodSource("checkGoals")
    void checkGoalPassesWhenAllFilesAreFormatted(final Function<Path, JavafmtCheckMojo> checkGoal, @TempDir final Path dir) throws Exception {
        Files.writeString(sourceDirectory(dir).resolve("Foo.java"), FORMATTED);
        assertThatNoException().isThrownBy(checkGoal.apply(dir)::execute);
        assertThatNoException().isThrownBy(checkGoal.apply(dir)::execute);
    }

    @ParameterizedTest
    @MethodSource("checkGoals")
    void checkGoalFailsAndNamesTheUnformattedFiles(final Function<Path, JavafmtCheckMojo> checkGoal, @TempDir final Path dir) throws Exception {
        final var file = Files.writeString(sourceDirectory(dir).resolve("Unformatted.java"), UNFORMATTED);
        assertThatThrownBy(checkGoal.apply(dir)::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("javafmt check failed: 1 file(s) not formatted")
                .hasMessageContaining(file.toString());
    }

    @Test
    void checkGoalSkipsParseErrorFilesWithoutFailing(@TempDir final Path dir) throws Exception {
        Files.writeString(sourceDirectory(dir).resolve("Bad.java"), "this is not valid java source code");
        assertThatNoException().isThrownBy(checkMojo(dir)::execute);
    }

    @Test
    void aSecondCheckOnTheSameCacheStillFails(@TempDir final Path dir) throws Exception {
        final var file = Files.writeString(sourceDirectory(dir).resolve("Unformatted.java"), UNFORMATTED);
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
        Files.writeString(sourceDirectory(dir).resolve("Warns.java"), REASSIGNS_A_PARAMETER);
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
                .hasMessageNotContaining("Order.java");
    }

    @Test
    void checkGoalWithAnUnknownRatchetRefFailsAndNamesTheRef(@TempDir final Path dir) {
        final var mojo = new JavafmtCheckMojo(projectInRepositoryWithAnEditedFile(dir), List.of(), Optional.empty(), Optional.of("no-such-branch"));
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessage("javafmt: unknown git ref: no-such-branch");
    }

    @Test
    void checkGoalFailsOnlyForUnformattedFilesOutsideExcludes(@TempDir final Path dir) throws Exception {
        final var src = sourceDirectory(dir);
        final var fixture = Files.createDirectories(src.resolve("test-fixtures"))
                .resolve("input.java");
        Files.writeString(fixture, UNFORMATTED);
        final var normal = Files.writeString(src.resolve("Normal.java"), UNFORMATTED);
        final var mojo = new JavafmtCheckMojo(projectInMavenLayout(dir), List.of("**/input.java"), Optional.empty(), Optional.empty());
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("1 file(s) not formatted")
                .hasMessageContaining(normal.toString())
                .hasMessageNotContaining(fixture.toString());
    }
}
