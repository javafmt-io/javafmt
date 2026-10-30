package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the boundary this module depends on to load in JBR 21: no dependency on javafmt-core,
 * and bytecode compiled no higher than what JBR 21 can read. Without this, a convenient import
 * or a stray classpath dependency compiles fine and fails only when a user tries to load the
 * plugin.
 */
class BytecodeTargetTest {

    /** JBR 21 in IDEA 2025.2 reads at most class file major 65. */
    private static final int MAX_MAJOR = 65;

    /**
     * Matches "io.javafmt." not followed by "intellij" or "daemon.protocol" as a whole word, so it
     * catches any cross-package reference to javafmt-core — a fully-qualified use as well as an
     * import — while leaving this module's own package statements, self-references and the
     * generated daemon protocol alone.
     */
    private static final Pattern CORE_REFERENCE = Pattern.compile("io\\.javafmt\\.(?!intellij\\b|daemon\\.protocol\\b)");

    @Test
    void everyShimClassLoadsInJbr21() throws IOException {
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
                    .filter(p -> majorVersion(p) > MAX_MAJOR)
                    .map(Path::toString)
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    // Test sources are deliberately exempt from this check: they run on the Java 25 launcher this
    // module's `test` task pins, never inside JBR 21, so a major-69 class on the test classpath —
    // e.g. javafmt-core's test-fixtures, used by JavafmtExternalFormatProcessorTest — is harmless.
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
                    .as("javafmt-core is class file major 69 and cannot load in JBR 21")
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
