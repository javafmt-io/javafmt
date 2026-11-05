package io.javafmt.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BinaryIdentityTest {
    @Test
    void identifiesAnExistingFileByPathTimeAndSize(@TempDir final Path dir) throws IOException {
        final var file = Files.writeString(dir.resolve("javafmt"), "binary");
        assertThat(BinaryIdentity.of(file)).hasValueSatisfying(identity -> {
            assertThat(identity.path()).isEqualTo(file);
            assertThat(identity.size()).isEqualTo(6);
        });
    }

    @Test
    void aFileThatCannotBeReadHasNoIdentity(@TempDir final Path dir) {
        assertThat(BinaryIdentity.of(dir.resolve("missing"))).isEmpty();
    }
}
