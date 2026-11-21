package io.javafmt.spotless;

import io.javafmt.PathFilter;
import io.javafmt.ProjectFile;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

record ProjectFiles(Keyed governing, List<Keyed> below, List<String> repositoryRoots) implements Serializable {

    ProjectFiles {
        Objects.requireNonNull(governing, "governing");
        below = List.copyOf(below);
        repositoryRoots = List.copyOf(repositoryRoots);
    }

    static ProjectFiles under(final Path directory) {
        final var root = directory.toAbsolutePath().normalize();
        final var finder = new Finder(root);
        try {
            Files.walkFileTree(root, finder);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to walk " + root, e);
        }
        return new ProjectFiles(
                governing(root),
                finder.projectFiles.stream()
                        .sorted()
                        .map(file -> keyed(root, file))
                        .toList(),
                finder.repositoryRoots.stream()
                        .sorted()
                        .map(repository -> root.relativize(repository).toString())
                        .toList());
    }

    private static Keyed governing(final Path root) {
        return switch (ProjectFile.forDirectory(root)) {
            case ProjectFile.Settings(var location, var _) -> keyed(root, Path.of(location));
            case ProjectFile.Invalid(var location, var _) -> keyed(root, Path.of(location));
            case ProjectFile.None() -> Keyed.NONE;
        };
    }

    private static Keyed keyed(final Path root, final Path file) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            content = "unreadable: " + e.getClass().getName();
        }
        return new Keyed(root.relativize(file).toString(), content);
    }

    record Keyed(String path, String content) implements Serializable {

        static final Keyed NONE = new Keyed("", "");

        Keyed {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(content, "content");
        }
    }

    private static final class Finder extends SimpleFileVisitor<Path> {

        private final Path root;

        private final List<Path> projectFiles = new ArrayList<>();

        private final List<Path> repositoryRoots = new ArrayList<>();

        private Finder(final Path root) {
            this.root = root;
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path directory, final BasicFileAttributes attributes) {
            if (directory.equals(root)) {
                return FileVisitResult.CONTINUE;
            }
            if (isGit(directory)) {
                if (root.equals(directory.getParent())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                repositoryRoots.add(Objects.requireNonNull(directory.getParent(), "parent"));
                return FileVisitResult.SKIP_SUBTREE;
            }
            return PathFilter.isDefaultExcludedDirectory(directory) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) {
            final var directory = Objects.requireNonNull(file.getParent(), "parent");
            if (directory.equals(root)) {
                return FileVisitResult.CONTINUE;
            }
            if (isGit(file)) {
                repositoryRoots.add(directory);
            } else if (ProjectFile.NAME.equals(String.valueOf(file.getFileName())) && Files.isRegularFile(file)) {
                projectFiles.add(file);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(final Path file, final IOException error) {
            return FileVisitResult.CONTINUE;
        }

        private static boolean isGit(final Path path) {
            return ".git".equals(String.valueOf(path.getFileName()));
        }
    }
}
