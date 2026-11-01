package io.javafmt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record GitRatchet(Set<Path> changedFiles) {
    private static final String GIT = "git";

    private static final Pattern NUL = Pattern.compile("\0");

    public GitRatchet {
        changedFiles = Set.copyOf(changedFiles);
    }

    public static GitRatchet since(final String ref, final Path directory) {
        return since(ref, directory, GIT);
    }

    static GitRatchet since(final String ref, final Path directory, final String git) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(git, "git");
        final var notARepository = "not inside a git repository: " + directory;
        if (!Files.isDirectory(directory)) {
            throw new GitRatchetException(notARepository);
        }
        final var root = realPath(Path.of(run(git, directory, notARepository, "rev-parse", "--show-toplevel").strip()));
        final var commit = commit(git, root, ref);
        final var base = run(git, root, "no common ancestor of HEAD and " + ref, "merge-base", "HEAD", commit).strip();
        final var changed = run(
                git, root, "git diff failed", "diff", "--name-only", "-z", "--no-renames", "--no-ext-diff", "--diff-filter=d", base, "--");
        final var untracked = run(git, root, "git ls-files failed", "ls-files", "--others", "--exclude-standard", "-z");
        return new GitRatchet(
                Stream.of(changed, untracked)
                        .flatMap(NUL::splitAsStream)
                        .filter(name -> !name.isEmpty())
                        .map(name -> root.resolve(name).normalize())
                        .collect(Collectors.toUnmodifiableSet()));
    }

    public boolean includes(final Path file) {
        return changedFiles.contains(realPath(file));
    }

    public List<Path> keep(final List<Path> files) {
        return files.stream().filter(this::includes).toList();
    }

    private static String commit(final String git, final Path root, final String ref) {
        final var unknown = "unknown git ref: " + ref;
        if (ref.isBlank() || ref.startsWith("-")) {
            throw new GitRatchetException(unknown);
        }
        return run(git, root, unknown, "rev-parse", "--verify", "--quiet", ref + "^{commit}").strip();
    }

    private static String run(final String git, final Path directory, final String failure, final String... arguments) {
        final var process = start(git, directory, arguments);
        final var errors = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()), Thread::startVirtualThread);
        final var output = read(process.getInputStream());
        if (exitCode(process) != 0) {
            final var detail = errors.join().strip();
            throw new GitRatchetException(detail.isEmpty() ? failure : failure + ": " + detail);
        }
        return output;
    }

    private static Process start(final String git, final Path directory, final String... arguments) {
        try {
            return new ProcessBuilder(Stream.concat(Stream.of(git), Stream.of(arguments)).toList()).directory(directory.toFile()).start();
        } catch (final IOException e) {
            throw new GitRatchetException("git is not installed or not on the PATH: " + git, e);
        }
    }

    private static String read(final InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new GitRatchetException("could not read the output of git", e);
        }
    }

    private static int exitCode(final Process process) {
        try {
            return process.waitFor();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitRatchetException("interrupted while waiting for git", e);
        }
    }

    private static Path realPath(final Path path) {
        try {
            return path.toRealPath();
        } catch (final IOException _) {
            return path.toAbsolutePath().normalize();
        }
    }
}
