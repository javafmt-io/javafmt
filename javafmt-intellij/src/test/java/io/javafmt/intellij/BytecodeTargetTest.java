package io.javafmt.intellij;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the boundary this module depends on in the IDE: main classes compile for Java 25, the
 * runtime IntelliJ 2026.2 ships. Without this, a build change that targets a newer release
 * compiles fine and fails only when a user tries to load the plugin.
 */
class BytecodeTargetTest {

    /** Class file major version of Java 25. */
    private static final int JAVA_25_MAJOR = 69;

    @Test
    void everyMainClassTargetsJava25() throws IOException {
        final var classesDir = Path.of("build/classes/java/main");
        assertThat(Files.isDirectory(classesDir)).as("compile the module before running this test").isTrue();
        try (final var files = Files.walk(classesDir)) {
            final var classFiles = files.filter(p -> p.toString().endsWith(".class")).toList();
            // An empty stream would trivially satisfy "no offenders" below, so require that we
            // actually examined something.
            assertThat(classFiles).as("expected at least one compiled class under %s", classesDir).isNotEmpty();
            final var offenders = classFiles.stream().filter(p -> majorVersion(p) != JAVA_25_MAJOR).map(Path::toString).toList();
            assertThat(offenders).isEmpty();
        }
    }

    private static int majorVersion(final Path classFile) {
        try (final var in = new DataInputStream(Files.newInputStream(classFile))) {
            in.readInt();
            in.readUnsignedShort();
            return in.readUnsignedShort();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
