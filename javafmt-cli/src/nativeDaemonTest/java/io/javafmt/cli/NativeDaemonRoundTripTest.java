package io.javafmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.daemon.DaemonFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NativeDaemonRoundTripTest {

    private static final Path BINARY_DIR = Path.of(System.getProperty("javafmt.test.nativeBinaryDir", "build/native/nativeCompile"));

    @Test
    void servesTheBinaryProtocol() throws IOException, InterruptedException {
        final var out = serve(DaemonFixtures.bytes(DaemonFixtures.REQUESTS_HEX), "--daemon");
        assertThat(DaemonFixtures.hexFrames(out)).containsExactlyInAnyOrderElementsOf(DaemonFixtures.REPLIES_HEX);
    }

    @Test
    void servesTheJsonProtocol() throws IOException, InterruptedException {
        final var out = serve(DaemonFixtures.text(DaemonFixtures.REQUESTS_JSON).getBytes(StandardCharsets.UTF_8), "--daemon", "--json");
        assertThat(new String(out, StandardCharsets.UTF_8).lines().toList()).containsExactlyInAnyOrderElementsOf(DaemonFixtures.REPLIES_JSON);
    }

    private static byte[] serve(final byte[] requests, final String... args) throws IOException, InterruptedException {
        final var binary = locateBinary().orElseThrow(() -> new AssertionError("no native javafmt binary in " + BINARY_DIR.toAbsolutePath()));
        final var command = Stream.concat(Stream.of(binary.toString()), Stream.of(args)).toList();
        final var process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (final var stdin = process.getOutputStream()) {
            stdin.write(requests);
        }
        final var out = process.getInputStream().readAllBytes();
        assertThat(process.waitFor(30, TimeUnit.SECONDS))
                .as("daemon exited before timeout")
                .isTrue();
        assertThat(process.exitValue()).isZero();
        return out;
    }

    private static Optional<Path> locateBinary() {
        return List.of(BINARY_DIR.resolve("javafmt"), BINARY_DIR.resolve("javafmt.exe"))
                .stream()
                .filter(Files::isExecutable)
                .findFirst();
    }
}
