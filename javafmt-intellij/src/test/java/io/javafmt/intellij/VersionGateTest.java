package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VersionGateTest {

    @Test
    void aConclusiveProbeIsAskedOnce(@TempDir final Path dir) throws IOException {
        final var probes = new AtomicInteger();
        final var gate = new VersionGate(
                binary -> {
                    probes.incrementAndGet();
                    return new JavafmtBinary.VersionProbe.Reported("javafmt 999.0.0");
                });
        final var binary = binary(dir);
        assertThat(List.of(gate.isSupported(binary), gate.isSupported(binary))).containsExactly(Optional.of(true), Optional.of(true));
        assertThat(probes).hasValue(1);
    }

    @Test
    void aBinaryThatFailsTheProbeIsRememberedAsUnsupported(@TempDir final Path dir) throws IOException {
        final var probes = new AtomicInteger();
        final var gate = new VersionGate(
                binary -> {
                    probes.incrementAndGet();
                    return new JavafmtBinary.VersionProbe.Failed();
                });
        final var binary = binary(dir);
        assertThat(List.of(gate.isSupported(binary), gate.isSupported(binary))).containsExactly(Optional.of(false), Optional.of(false));
        assertThat(probes).hasValue(1);
    }

    @Test
    void anInterruptedOrTimedOutProbeIsNotCached(@TempDir final Path dir) throws IOException {
        final var answers = new ArrayDeque<JavafmtBinary.VersionProbe>(
                List.of(new JavafmtBinary.VersionProbe.Inconclusive(), new JavafmtBinary.VersionProbe.Reported("javafmt 999.0.0")));
        final var gate = new VersionGate(binary -> answers.removeFirst());
        final var binary = binary(dir);
        assertThat(List.of(gate.isSupported(binary), gate.isSupported(binary))).containsExactly(Optional.empty(), Optional.of(true));
    }

    private static BinaryIdentity binary(final Path dir) throws IOException {
        return BinaryIdentity.of(Files.writeString(dir.resolve("javafmt"), "x")).orElseThrow();
    }
}
