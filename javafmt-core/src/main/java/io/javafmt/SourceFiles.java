package io.javafmt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * Discovery of {@code .java} regular files, sorted for deterministic output order, and the
 * in-place write every integration uses to put formatted source back on disk.
 */
@UtilityClass
public final class SourceFiles {

    private final String TEMP_SUFFIX = ".javafmt";

    /**
     * The {@code .java} files under {@code root}, skipping the build-output and dependency trees
     * named by {@link PathFilter#DEFAULT_EXCLUDED_DIRECTORIES}. An unreadable directory is
     * reported in {@link Discovery#unreadable()} and the rest of the tree is still searched.
     */
    public Discovery discover(final Path root) {
        return discover(root, true);
    }

    /** As {@link #discover}, but searching every directory, including the ones normally skipped. */
    public Discovery discoverWithoutDefaultExcludes(final Path root) {
        return discover(root, false);
    }

    /** {@link #discover}, with an unreadable directory raised instead of reported. */
    public List<Path> walk(final Path root) {
        return discover(root).filesOrThrow();
    }

    private Discovery discover(final Path root, final boolean applyDefaultExcludes) {
        Objects.requireNonNull(root, "root");
        if (!Files.isDirectory(root)) {
            return new Discovery(List.of(), List.of());
        }
        final var visitor = new Collector(root, applyDefaultExcludes);
        try {
            Files.walkFileTree(root, visitor);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk " + root, e);
        }
        return visitor.discovery();
    }

    /**
     * Replaces {@code file}'s contents with {@code content}, written UTF-8.
     *
     * <p>The new content goes to a sibling temp file that is then moved over the target, so an
     * interrupted run leaves the source file whole rather than truncated. The target's permissions
     * are carried onto the replacement, and a symlink argument is resolved first so the link
     * survives the move. A target the caller may not write is refused, as a plain write would be.
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    public void write(final Path file, final String content) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(content, "content");
        try {
            final var target = Files.exists(file) ? file.toRealPath() : createEmpty(file);
            if (!Files.isWritable(target)) {
                throw new AccessDeniedException(target.toString());
            }
            swapIntoPlace(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }

    private Path createEmpty(final Path file) throws IOException {
        Files.createFile(file);
        return file.toRealPath();
    }

    private void swapIntoPlace(final Path target, final String content) throws IOException {
        final var directory = Objects.requireNonNull(target.getParent(), "parent of " + target);
        final var temp = Files.createTempFile(directory, target.getFileName().toString(), TEMP_SUFFIX);
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            copyPermissions(target, temp);
            move(temp, target);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw e;
        }
    }

    private void copyPermissions(final Path from, final Path to) throws IOException {
        if (from.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from));
        }
    }

    private void move(final Path temp, final Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteQuietly(final Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // The write already failed; the caller is told about that, not about the leftover.
        }
    }

    /** An entry the walk could not read, kept so the caller can report it and set an exit code. */
    public record Unreadable(Path path, IOException error) {}

    /** What a walk found, alongside the entries it could not read and skipped. */
    public record Discovery(List<Path> files, List<Unreadable> unreadable) {

        public Discovery {
            files = List.copyOf(files);
            unreadable = List.copyOf(unreadable);
        }

        /**
         * The files found, or the first failure rethrown, for callers that must not carry on as
         * though an unreadable directory were empty.
         */
        public List<Path> filesOrThrow() {
            unreadable.stream()
                    .findFirst()
                    .ifPresent(u -> {
                        throw new UncheckedIOException("Failed to walk " + u.path(), u.error());
                    });
            return files;
        }
    }

    /**
     * Collects as it walks, because that is the contract {@link Files#walkFileTree} calls back
     * into. Recording a failed entry and continuing is the whole point: one directory the user
     * cannot read must not hide the siblings they can.
     */
    private static final class Collector extends SimpleFileVisitor<Path> {

        private final Path root;

        private final boolean applyDefaultExcludes;

        private final List<Path> files = new ArrayList<>();

        private final List<Unreadable> unreadable = new ArrayList<>();

        private Collector(final Path root, final boolean applyDefaultExcludes) {
            this.root = root;
            this.applyDefaultExcludes = applyDefaultExcludes;
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
            final var pruned = applyDefaultExcludes
                && !dir.equals(root)
                && PathFilter.isDefaultExcludedDirectory(dir);
            return pruned ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
            if (file.toString().endsWith(".java") && Files.isRegularFile(file)) {
                files.add(file);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
            unreadable.add(new Unreadable(file, exc));
            return FileVisitResult.CONTINUE;
        }

        private Discovery discovery() {
            return new Discovery(files.stream().sorted().toList(), List.copyOf(unreadable));
        }
    }
}
