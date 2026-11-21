package io.javafmt.spotless;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFilesTest {

    @TempDir
    private Path project;

    @Test
    void underListsTheProjectFilesBelowAndTheRepositoryRootsInPathOrder() throws Exception {
        Files.createDirectories(project.resolve(".git"));
        Files.writeString(project.resolve(".javafmt"), "root");
        Files.writeString(Files.createDirectories(project.resolve("b/src")).resolve(".javafmt"), "b");
        Files.writeString(Files.createDirectories(project.resolve("a")).resolve(".javafmt"), "a");
        Files.writeString(Files.createDirectories(project.resolve("module")).resolve(".git"), "gitdir: x\n");
        Files.writeString(
                Files.createDirectories(project.resolve("module/inner")).resolve(".javafmt"), "inner");
        Files.createDirectories(project.resolve("clone/.git"));
        Files.writeString(project.resolve("clone/.javafmt"), "clone");
        Files.writeString(Files.createDirectories(project.resolve("build")).resolve(".javafmt"), "built");

        final var files = ProjectFiles.under(project);

        assertThat(files.governing()).isEqualTo(new ProjectFiles.Keyed(".javafmt", "root"));
        assertThat(files.below())
                .containsExactly(new ProjectFiles.Keyed("a/.javafmt", "a"),
                        new ProjectFiles.Keyed("b/src/.javafmt", "b"),
                        new ProjectFiles.Keyed("clone/.javafmt", "clone"),
                        new ProjectFiles.Keyed("module/inner/.javafmt", "inner"));
        assertThat(files.repositoryRoots()).containsExactly("clone", "module");
    }

    @Test
    void underAnEmptyProjectHasNoGoverningFileAndNothingBelow() {
        final var files = ProjectFiles.under(project);
        assertThat(files.governing()).isEqualTo(ProjectFiles.Keyed.NONE);
        assertThat(files.below()).isEmpty();
        assertThat(files.repositoryRoots()).isEqualTo(List.of());
    }
}
