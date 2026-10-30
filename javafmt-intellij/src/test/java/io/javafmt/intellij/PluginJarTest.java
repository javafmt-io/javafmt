package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.daemon.DaemonFixtures;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;

class PluginJarTest {

    private static final int MAX_MAJOR = 65;

    @Test
    void protobufIsBundledRelocatedSoItCannotClashWithIntellijsOwn() throws IOException {
        assertThat(entries())
                .contains(
                        "io/javafmt/daemon/protocol/FormatRequest.class",
                        "io/javafmt/intellij/protobuf/GeneratedMessage.class",
                        "io/javafmt/intellij/validate/ValidateProto.class",
                        "io/javafmt/intellij/native-checksums.txt")
                .noneMatch(name -> name.startsWith("com/google/") || name.startsWith("build/buf/") || name.startsWith("dev/cel/"));
    }

    @Test
    void theBundledProtocolDecodesAReplyWithNothingElseOnTheClasspath() throws Exception {
        final var jar = Path.of(System.getProperty("javafmt.pluginJar")).toUri().toURL();
        try (final var loader = new URLClassLoader(new URL[] {jar,}, ClassLoader.getPlatformClassLoader())) {
            final var replyClass = loader.loadClass("io.javafmt.daemon.protocol.FormatReply");
            final var bytes = DaemonFixtures.bytes(List.of(DaemonFixtures.REPLIES_HEX.getFirst()));
            final var reply = replyClass.getMethod("parseDelimitedFrom", InputStream.class).invoke(null, new ByteArrayInputStream(bytes));
            assertThat(replyClass.getMethod("getOutput").invoke(reply)).isEqualTo("class A {}\n");
        }
    }

    @Test
    void everyBundledClassLoadsInJbr21() throws IOException {
        try (final var jar = new JarFile(System.getProperty("javafmt.pluginJar"))) {
            assertThat(
                    jar.stream()
                            .filter(entry -> entry.getName().endsWith(".class"))
                            .filter(entry -> !entry.getName().startsWith("META-INF/versions/")))
                    .isNotEmpty()
                    .allSatisfy(entry -> assertThat(majorVersion(jar, entry)).as(entry.getName()).isLessThanOrEqualTo(MAX_MAJOR));
        }
    }

    private static List<String> entries() throws IOException {
        try (final var jar = new JarFile(System.getProperty("javafmt.pluginJar"))) {
            return jar.stream().map(ZipEntry::getName).toList();
        }
    }

    private static int majorVersion(final JarFile jar, final ZipEntry entry) {
        try (final var in = new DataInputStream(jar.getInputStream(entry))) {
            in.readInt();
            in.readUnsignedShort();
            return in.readUnsignedShort();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
