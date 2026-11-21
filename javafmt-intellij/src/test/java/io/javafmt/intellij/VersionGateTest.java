package io.javafmt.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VersionGateTest {

    static Stream<Arguments> conclusiveProbes() {
        return Stream.of(
                Arguments.of(new JavafmtBinary.VersionProbe.Reported("javafmt 999.0.0"), VersionGate.Verdict.SUPPORTED),
                Arguments.of(new JavafmtBinary.VersionProbe.Reported("javafmt 0.0.1"), VersionGate.Verdict.TOO_OLD),
                Arguments.of(new JavafmtBinary.VersionProbe.Failed(), VersionGate.Verdict.TOO_OLD));
    }

    @ParameterizedTest
    @MethodSource("conclusiveProbes")
    void aConclusiveProbeIsAskedOnce(final JavafmtBinary.VersionProbe answer, final VersionGate.Verdict verdict, @TempDir final Path dir)
            throws IOException {
        final var probes = new AtomicInteger();
        final var gate = new VersionGate(_ -> {
            probes.incrementAndGet();
            return answer;
        });
        final var binary = binary(dir);
        assertThat(List.of(gate.verdict(binary), gate.verdict(binary))).containsExactly(verdict, verdict);
        assertThat(probes).hasValue(1);
    }

    @Test
    void anInterruptedOrTimedOutProbeIsNotCached(@TempDir final Path dir) throws IOException {
        final var answers = new ArrayDeque<JavafmtBinary.VersionProbe>(
                List.of(new JavafmtBinary.VersionProbe.Inconclusive(), new JavafmtBinary.VersionProbe.Reported("javafmt 999.0.0")));
        final var gate = new VersionGate(_ -> answers.removeFirst());
        final var binary = binary(dir);
        assertThat(List.of(gate.verdict(binary), gate.verdict(binary))).containsExactly(VersionGate.Verdict.UNKNOWN, VersionGate.Verdict.SUPPORTED);
    }

    private static BinaryIdentity binary(final Path dir) throws IOException {
        return BinaryIdentity.of(Files.writeString(dir.resolve("javafmt"), "x")).orElseThrow();
    }
}
