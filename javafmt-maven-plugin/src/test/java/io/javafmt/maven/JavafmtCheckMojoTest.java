package io.javafmt.maven;

import static io.javafmt.maven.MojoTestSupport.UNFORMATTED;
import static io.javafmt.maven.MojoTestSupport.buildProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.javafmt.Javafmt;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavafmtCheckMojoTest {

    private static final String FORMATTED = Javafmt.format(UNFORMATTED);

    @Test
    void emptyFileIsProcessedNotMistakenForCacheHit(@TempDir final Path dir) throws Exception {
        final var src = Files.createDirectories(dir.resolve("src/main/java"));
        Files.writeString(src.resolve("Empty.java"), "");
        Files.writeString(src.resolve("Unformatted.java"), UNFORMATTED);
        final var mojo = new JavafmtCheckMojo(buildProject(dir));
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
        final var mojo = new JavafmtCheckMojo(buildProject(dir), true, cacheFile);
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
        new JavafmtCheckMojo(buildProject(dir)).execute(); // must not throw
    }

    @Test
    void checkGoalPassesWhenAllFilesFormatted(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, Javafmt.format(UNFORMATTED), StandardCharsets.UTF_8);
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir));
        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void checkGoalFailsWithUnformattedFilesNamed(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Unformatted.java");
        Files.writeString(file, UNFORMATTED, StandardCharsets.UTF_8);
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir));
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("javafmt check failed: 1 file(s) not formatted")
                .hasMessageContaining(file.toString());
    }

    @Test
    void checkGoalSkipsParseErrorFilesWithoutFailing(@TempDir final Path srcDir) throws Exception {
        final var file = srcDir.resolve("Bad.java");
        Files.writeString(file, "this is not valid java source code", StandardCharsets.UTF_8);
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir));
        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void checkGoal_withCacheEnabled_doesNotFailOnUpToDateFile(@TempDir final Path srcDir, @TempDir final Path cacheDir) throws Exception {
        final var formatted = Javafmt.format(UNFORMATTED);
        final var file = srcDir.resolve("Foo.java");
        Files.writeString(file, formatted, StandardCharsets.UTF_8);
        final var cacheFile = cacheDir.resolve("javafmt.cache");
        final var first = new JavafmtCheckMojo(buildProject(srcDir), true, cacheFile);
        assertThatNoException().isThrownBy(first::execute);
        final var second = new JavafmtCheckMojo(buildProject(srcDir), true, cacheFile);
        assertThatNoException().isThrownBy(second::execute);
    }

    @Test
    void checkGoal_withCacheEnabled_stillFailsOnUnformattedFile(@TempDir final Path srcDir, @TempDir final Path cacheDir) throws Exception {
        final var file = srcDir.resolve("Unformatted.java");
        Files.writeString(file, UNFORMATTED, StandardCharsets.UTF_8);
        final var cacheFile = cacheDir.resolve("javafmt.cache");
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir), true, cacheFile);
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(file.toString());
    }

    @Test
    void checkGoalIgnoresExcludedUnformattedFiles(@TempDir final Path srcDir) throws Exception {
        final var fixture = srcDir.resolve("test-fixtures/input.java");
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, UNFORMATTED, StandardCharsets.UTF_8);
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir), List.of("**/input.java"));
        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void checkGoalStillFailsForUnformattedFilesOutsideExcludes(@TempDir final Path srcDir) throws Exception {
        final var fixture = srcDir.resolve("test-fixtures/input.java");
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, UNFORMATTED, StandardCharsets.UTF_8);
        final var normal = srcDir.resolve("Normal.java");
        Files.writeString(normal, UNFORMATTED, StandardCharsets.UTF_8);
        final var mojo = new JavafmtCheckMojo(buildProject(srcDir), List.of("**/input.java"));
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(normal.toString())
                .satisfies(t -> assertThat(t.getMessage()).doesNotContain(fixture.toString()));
    }
}
