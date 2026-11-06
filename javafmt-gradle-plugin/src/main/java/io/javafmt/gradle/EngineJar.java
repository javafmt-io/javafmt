package io.javafmt.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

final class EngineJar {

    private static final String RESOURCE = "javafmt-engine.jar";

    static File extractTo(final File directory, final String hash) {
        final var target = directory.toPath().resolve("javafmt-engine-" + hash + ".jar");
        if (Files.exists(target)) {
            return target.toFile();
        }
        try (final var in = Objects.requireNonNull(EngineJar.class.getResourceAsStream(RESOURCE), RESOURCE)) {
            Files.createDirectories(directory.toPath());
            final var temp = Files.createTempFile(directory.toPath(), "javafmt-engine", ".tmp");
            try {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                move(temp, target);
            } finally {
                Files.deleteIfExists(temp);
            }
            return target.toFile();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void move(final Path temp, final Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (final FileAlreadyExistsException | AccessDeniedException e) {
            if (!Files.exists(target)) {
                throw e;
            }
        }
    }

    private EngineJar() {}
}
