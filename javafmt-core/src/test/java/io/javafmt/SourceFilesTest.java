package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFilesTest {

    @TempDir Path dir;

    @Test
    void skipsDirectoriesNamedLikeJavaFilesAndSortsResults() throws IOException {
        Files.createDirectories(dir.resolve("Trap.java"));
        Files.writeString(dir.resolve("B.java"), "class B {}");
        Files.writeString(dir.resolve("A.java"), "class A {}");
        Files.writeString(dir.resolve("Trap.java/NotSource.txt"), "x");
        assertThat(SourceFiles.walk(dir)).containsExactly(dir.resolve("A.java"), dir.resolve("B.java"));
    }

    @Test
    void nonDirectoryYieldsEmpty() {
        assertThat(SourceFiles.walk(dir.resolve("missing"))).isEmpty();
    }

    @Test
    void walkSkipsTheOutputAndDependencyTreesBelowTheRoot() throws IOException {
        Files.createDirectories(dir.resolve("build/generated"));
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("build/generated/Gen.java"), "class Gen {}");
        Files.writeString(dir.resolve("src/S.java"), "class S {}");
        assertThat(SourceFiles.walk(dir)).containsExactly(dir.resolve("src/S.java"));
    }

    @Test
    void discoverWithoutDefaultExcludesSeesEveryJavaFile() throws IOException {
        Files.createDirectories(dir.resolve("build/generated"));
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("build/generated/Gen.java"), "class Gen {}");
        Files.writeString(dir.resolve("src/S.java"), "class S {}");
        assertThat(SourceFiles.discoverWithoutDefaultExcludes(dir).files())
                .containsExactly(dir.resolve("build/generated/Gen.java"), dir.resolve("src/S.java"));
    }

    @Test
    void discoverReportsTheDirectoryItCouldNotReadAndKeepsTheFilesItCould() throws IOException {
        final var unreadable = Files.createDirectory(dir.resolve("locked"));
        Files.writeString(unreadable.resolve("Locked.java"), "class Locked {}");
        Files.writeString(dir.resolve("Open.java"), "class Open {}");
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));
        try {
            final var discovery = SourceFiles.discover(dir);
            assertThat(discovery.files()).containsExactly(dir.resolve("Open.java"));
            assertThat(discovery.unreadable())
                    .singleElement()
                    .satisfies(u -> {
                        assertThat(u.path()).isEqualTo(unreadable);
                        assertThat(u.error()).isInstanceOf(AccessDeniedException.class);
                    });
        } finally {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void walkStillThrowsSoACallerThatCannotContinuePastAnUnreadableDirectoryDoesNot() throws IOException {
        final var unreadable = Files.createDirectory(dir.resolve("locked"));
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));
        try {
            assertThatThrownBy(() -> SourceFiles.walk(dir))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasCauseInstanceOf(AccessDeniedException.class);
        } finally {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rwx------"));
        }
    }

    /** The excluded names are tested below the root, so naming one of them as the root still works. */
    @Test
    void walkOfADirectoryNamedLikeADefaultExcludeStillFindsItsFiles() throws IOException {
        final var build = Files.createDirectory(dir.resolve("build"));
        Files.writeString(build.resolve("Gen.java"), "class Gen {}");
        assertThat(SourceFiles.walk(build)).containsExactly(build.resolve("Gen.java"));
    }

    /**
     * A truncate-then-write leaves a reader whatever bytes reached the disk before the crash. An
     * atomic write builds the new content in a separate file and moves it over the old one, so the
     * target always carries one complete version or the other — which on a POSIX file system shows
     * up as a new file key under the same path.
     */
    @Test
    void writeSwapsANewFileIntoPlaceInsteadOfTruncatingTheOldOne() throws IOException {
        final var file = dir.resolve("A.java");
        Files.writeString(file, "class A {}");
        final var before = Files.readAttributes(file, BasicFileAttributes.class).fileKey();
        SourceFiles.write(file, "class A {\n}\n");
        assertThat(Files.readString(file)).isEqualTo("class A {\n}\n");
        assertThat(Files.readAttributes(file, BasicFileAttributes.class).fileKey()).isNotEqualTo(before);
    }

    @Test
    void writePreservesThePermissionsOfTheFileItReplaces() throws IOException {
        final var file = dir.resolve("A.java");
        Files.writeString(file, "class A {}");
        final var permissions = PosixFilePermissions.fromString("rw-r-----");
        Files.setPosixFilePermissions(file, permissions);
        SourceFiles.write(file, "class A {\n}\n");
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(permissions);
    }

    @Test
    void writeLeavesNoTemporaryFileBehind() throws IOException {
        final var file = dir.resolve("A.java");
        Files.writeString(file, "class A {}");
        SourceFiles.write(file, "class A {\n}\n");
        try (final var entries = Files.list(dir)) {
            assertThat(entries).containsExactly(file);
        }
    }

    @Test
    void writeFollowsASymlinkInsteadOfReplacingIt() throws IOException {
        final var real = Files.createDirectory(dir.resolve("real"));
        final var target = real.resolve("A.java");
        Files.writeString(target, "class A {}");
        final var link = Files.createSymbolicLink(dir.resolve("A.java"), target);
        SourceFiles.write(link, "class A {\n}\n");
        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(Files.readString(target)).isEqualTo("class A {\n}\n");
    }

    @Test
    void writeRefusesAFileThePermissionsSayIsNotWritable() throws IOException {
        final var file = dir.resolve("A.java");
        Files.writeString(file, "class A {}");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
        assertThat(Files.isWritable(file)).isFalse();
        assertThatThrownBy(() -> SourceFiles.write(file, "class A {\n}\n"))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(AccessDeniedException.class);
        assertThat(Files.readString(file)).isEqualTo("class A {}");
    }

    @Test
    void writeCreatesAFileThatDoesNotExistYet() throws IOException {
        final var file = dir.resolve("New.java");
        SourceFiles.write(file, "class New {}\n");
        assertThat(Files.exists(file, LinkOption.NOFOLLOW_LINKS)).isTrue();
        assertThat(Files.readString(file)).isEqualTo("class New {}\n");
    }
}
