package io.javafmt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

public record GitRepository(Path root) {

    public GitRepository {
        Objects.requireNonNull(root, "root");
    }

    public static GitRepository init(final Path root) {
        final var repository = new GitRepository(root);
        repository.git("init", "--quiet", "--initial-branch=main");
        return repository;
    }

    public Path write(final String relative, final String content) {
        final var path = root.resolve(relative);
        try {
            Files.createDirectories(Objects.requireNonNull(path.getParent(), "parent"));
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return path;
    }

    public void delete(final String relative) {
        try {
            Files.delete(root.resolve(relative));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void commitAll(final String message) {
        git("add", "--all");
        git("commit", "--quiet", "--allow-empty", "--message", message);
    }

    public String git(final String... arguments) {
        final var builder = new ProcessBuilder(
                        Stream.concat(Stream.of("git"), Stream.of(arguments)).toList())
                .directory(root.toFile())
                .redirectErrorStream(true);
        final var environment = builder.environment();
        environment.put("GIT_CONFIG_GLOBAL", nullDevice());
        environment.put("GIT_CONFIG_NOSYSTEM", "1");
        environment.put("GIT_AUTHOR_NAME", "Test");
        environment.put("GIT_AUTHOR_EMAIL", "test@example.com");
        environment.put("GIT_COMMITTER_NAME", "Test");
        environment.put("GIT_COMMITTER_EMAIL", "test@example.com");
        try {
            final var process = builder.start();
            process.getOutputStream().close();
            final var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IllegalStateException("git " + String.join(" ", arguments) + " failed: " + output);
            }
            return output;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String nullDevice() {
        return System.getProperty("os.name", "").startsWith("Windows") ? "NUL" : "/dev/null";
    }
}
