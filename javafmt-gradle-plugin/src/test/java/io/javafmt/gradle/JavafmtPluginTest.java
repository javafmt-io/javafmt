package io.javafmt.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JavafmtPluginTest {

    @Test
    void aGitDirectoryStopsTheWalk(@TempDir final Path dir) throws IOException {
        final var source = Files.createDirectories(dir.resolve("repo/module/src"));
        Files.createDirectories(dir.resolve("repo/.git"));
        assertThat(JavafmtPlugin.upToTheRepositoryRoot(source).toList()).containsExactly(source, dir.resolve("repo/module"), dir.resolve("repo"));
    }

    @Test
    void aGitFileStopsTheWalk(@TempDir final Path dir) throws IOException {
        final var source = Files.createDirectories(dir.resolve("worktree/module/src"));
        Files.writeString(dir.resolve("worktree/.git"), "gitdir: ../elsewhere\n");
        assertThat(JavafmtPlugin.upToTheRepositoryRoot(source).toList())
                .containsExactly(source, dir.resolve("worktree/module"), dir.resolve("worktree"));
    }

    @Test
    void theNearestGitEntryWins(@TempDir final Path dir) throws IOException {
        final var source = Files.createDirectories(dir.resolve("outer/inner/src"));
        Files.createDirectories(dir.resolve("outer/.git"));
        Files.createDirectories(dir.resolve("outer/inner/.git"));
        assertThat(JavafmtPlugin.upToTheRepositoryRoot(source).toList()).containsExactly(source, dir.resolve("outer/inner"));
    }

    @Test
    void withoutAGitEntryTheWalkEndsAtTheFilesystemRoot(@TempDir final Path dir) throws IOException {
        final var source = Files.createDirectories(dir.resolve("a/b"));
        final var walk = JavafmtPlugin.upToTheRepositoryRoot(source).toList();
        assertThat(walk).startsWith(source, dir.resolve("a"), dir);
        assertThat(walk.get(walk.size() - 1)).isEqualTo(source.toAbsolutePath().getRoot());
    }
}
