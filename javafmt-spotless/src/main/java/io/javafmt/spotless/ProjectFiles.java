package io.javafmt.spotless;

import io.javafmt.PathFilter;
import io.javafmt.ProjectFile;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

record ProjectFiles(ProjectFile governing, List<ProjectFile> below, List<String> repositoryRoots) implements Serializable {

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
                ProjectFile.forDirectory(root), finder.projectFiles.stream().sorted().map(ProjectFile::read).toList(),
                finder.repositoryRoots.stream().sorted().map(Path::toString).toList());
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
            } else if (ProjectFile.NAME.equals(String.valueOf(file.getFileName())) && attributes.isRegularFile()) {
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
