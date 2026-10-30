package io.javafmt.intellij;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.Optional;

record BinaryIdentity(Path path, FileTime lastModified, long size) {

    BinaryIdentity {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(lastModified, "lastModified");
    }

    static Optional<BinaryIdentity> of(final Path path) {
        try {
            return Optional.of(new BinaryIdentity(path, Files.getLastModifiedTime(path), Files.size(path)));
        } catch (final IOException e) {
            return Optional.empty();
        }
    }
}
