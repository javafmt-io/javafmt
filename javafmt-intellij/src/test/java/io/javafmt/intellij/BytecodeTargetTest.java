package io.javafmt.intellij;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the boundary this module depends on in the IDE: main classes compile for Java 25, the
 * runtime IntelliJ 2026.2 ships, and main sources never reference javafmt-core, because the plugin
 * formats through the native javafmt binary. Without this, a convenient import or a stray
 * classpath dependency compiles fine and fails only when a user tries to load the plugin.
 */
class BytecodeTargetTest {
    /** Class file major version of Java 25. */
    private static final int JAVA_25_MAJOR = 69;

    /**
     * Matches "io.javafmt." not followed by "intellij" or "daemon.protocol" as a whole word, so it
     * catches any cross-package reference to javafmt-core — a fully-qualified use as well as an
     * import — while leaving this module's own package statements, self-references and the
     * generated daemon protocol alone.
     */
    private static final Pattern CORE_REFERENCE = Pattern.compile("io\\.javafmt\\.(?!intellij\\b|daemon\\.protocol\\b)");

    @Test
    void everyMainClassTargetsJava25() throws IOException {
        final var classesDir = Path.of("build/classes/java/main");
        assertThat(Files.isDirectory(classesDir))
                .as("compile the module before running this test")
                .isTrue();
        try (final var files = Files.walk(classesDir)) {
            final var classFiles = files.filter(p -> p.toString().endsWith(".class")).toList();
            // An empty stream would trivially satisfy "no offenders" below, so require that we
            // actually examined something.
            assertThat(classFiles)
                    .as("expected at least one compiled class under %s", classesDir)
                    .isNotEmpty();
            final var offenders = classFiles.stream()
                    .filter(p -> majorVersion(p) != JAVA_25_MAJOR)
                    .map(Path::toString)
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    // Test sources are deliberately exempt from this check: they never load into the IDE, so
    // javafmt-core's test fixtures, used by JavafmtExternalFormatProcessorTest, are fine there.
    @Test
    void mainSourcesDoNotReferenceJavafmtCore() throws IOException {
        try (final var sources = Files.walk(Path.of("src/main/java"))) {
            final var javaFiles = sources.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(javaFiles).as("expected at least one main source file").isNotEmpty();
            final var offenders = javaFiles.stream()
                    .filter(BytecodeTargetTest::referencesCore)
                    .map(Path::toString)
                    .toList();
            assertThat(offenders)
                    .as("the plugin formats through the native javafmt binary and does not bundle javafmt-core")
                    .isEmpty();
        }
    }

    private static boolean referencesCore(final Path source) {
        try {
            return Files.readAllLines(source)
                    .stream()
                    .anyMatch(l -> CORE_REFERENCE.matcher(l).find());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
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
