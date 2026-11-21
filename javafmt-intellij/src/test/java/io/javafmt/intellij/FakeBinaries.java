package io.javafmt.intellij;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

final class FakeBinaries {

    static Path executable(final Path dir, final String name, final String script) throws IOException {
        final var path = Files.writeString(dir.resolve(name), script);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
    }

    private FakeBinaries() {}
}
