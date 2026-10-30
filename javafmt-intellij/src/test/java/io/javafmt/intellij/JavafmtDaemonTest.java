package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.execution.ExecutionException;
import io.javafmt.daemon.DaemonFixtures;
import io.javafmt.daemon.protocol.FormatReply;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.MAC, OS.LINUX})
class JavafmtDaemonTest {

    static final String READ_REQUEST = "n=$(($(dd bs=1 count=1 2>/dev/null | od -An -tu1)))\ndd bs=1 count=$n of=/dev/null 2>/dev/null\n";

    static final String ECHO_REQUESTS = "exec cat\n";

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Test
    void aRequestIsTheFixturesLengthDelimitedProtobuf(@TempDir final Path dir) throws Exception {
        final var sent = dir.resolve("sent");
        final var expected = DaemonFixtures.REQUESTS_HEX.getFirst();
        final var daemon = start(script(dir, "head -c " + expected.length() / 2 + " > '" + sent + "'\ncat '" + sent + "'\ncat > /dev/null\n"));
        try {
            daemon.format("class A{}");
            assertThat(HexFormat.of().formatHex(Files.readAllBytes(sent))).isEqualTo(expected);
        } finally {
            daemon.stop();
        }
    }

    @Test
    void theFixtureRepliesAreDecoded(@TempDir final Path dir) throws Exception {
        final var replies = DaemonFixtures.REPLIES_HEX.stream()
                .map(hex -> READ_REQUEST + printf(HexFormat.of().parseHex(hex)))
                .collect(Collectors.joining());
        final var daemon = start(script(dir, replies + "cat > /dev/null\n"));
        try {
            assertThat(List.of(daemon.format("class A{}"), daemon.format("class Broken {"), daemon.format("")))
                    .containsExactly(
                            new JavafmtBinary.Outcome.Formatted("class A {}\n", List.of()),
                            new JavafmtBinary.Outcome.Formatted(
                                    "class Broken {",
                                    List.of(
                                            FakeDaemon.diagnostic(
                                                    Severity.SEVERITY_ERROR,
                                                    Kind.KIND_PARSE,
                                                    "reached end of file while parsing",
                                                    1,
                                                    15))),
                            new JavafmtBinary.Outcome.Formatted(
                                    "",
                                    List.of(FakeDaemon.diagnostic(Severity.SEVERITY_ERROR, Kind.KIND_CLI, "source: value is required", 0, 0))));
        } finally {
            daemon.stop();
        }
    }

    @Test
    void answersEveryRequestFromOneLongLivedProcess(@TempDir final Path dir) throws Exception {
        final var starts = dir.resolve("starts");
        final var daemon = start(script(dir, "echo started >> '" + starts + "'\n" + ECHO_REQUESTS));
        try {
            assertThat(daemon.format("class A {}\n")).isEqualTo(new JavafmtBinary.Outcome.Formatted("class A {}\n", List.of()));
            assertThat(daemon.format("class B {}\n")).isEqualTo(new JavafmtBinary.Outcome.Formatted("class B {}\n", List.of()));
            assertThat(Files.readAllLines(starts)).hasSize(1);
        } finally {
            daemon.stop();
        }
    }

    @Test
    @Timeout(10)
    void eachReplyReachesTheRequestWithItsId(@TempDir final Path dir) throws Exception {
        final var first = dir.resolve("first");
        final var second = dir.resolve("second");
        final var daemon = start(
                script(
                        dir,
                        "dd bs=1 count=6 of='" + first + "' 2>/dev/null\ndd bs=1 count=6 of='" + second + "' 2>/dev/null\ncat '" + second + "' '"
                                + first
                                + "'\ncat > /dev/null\n"));
        try {
            final var a = CompletableFuture.supplyAsync(() -> daemon.format("a"));
            final var b = CompletableFuture.supplyAsync(() -> daemon.format("b"));
            assertThat(a.join()).isEqualTo(new JavafmtBinary.Outcome.Formatted("a", List.of()));
            assertThat(b.join()).isEqualTo(new JavafmtBinary.Outcome.Formatted("b", List.of()));
        } finally {
            daemon.stop();
        }
    }

    @Test
    void aReplyForAnotherRequestIsIgnored(@TempDir final Path dir) throws Exception {
        final var daemon = start(script(dir, READ_REQUEST + reply(99, "wrong") + reply(1, "right") + "cat > /dev/null\n"));
        try {
            assertThat(daemon.format("class A {}")).isEqualTo(new JavafmtBinary.Outcome.Formatted("right", List.of()));
        } finally {
            daemon.stop();
        }
    }

    @Test
    @Timeout(10)
    void aDaemonThatNeverAnswersIsRefusedAtTheTimeoutAndStopped(@TempDir final Path dir) throws Exception {
        final var daemon = start(script(dir, "cat > /dev/null\n"));
        final var begun = Instant.now();
        assertThat(daemon.format("class A {}")).isEqualTo(new JavafmtBinary.Outcome.Refused("javafmt did not respond within 2s", true));
        assertThat(Duration.between(begun, Instant.now())).isBetween(TIMEOUT, TIMEOUT.plusSeconds(2));
        assertThat(daemon.isAlive()).isFalse();
    }

    @Test
    @Timeout(10)
    void aDaemonThatNeverReadsItsInputDoesNotHoldTheCallerPastTheTimeout(@TempDir final Path dir) throws Exception {
        final var daemon = start(script(dir, "exec sleep 15\n"));
        final var begun = Instant.now();
        assertThat(daemon.format("x".repeat(1_000_000)))
                .isInstanceOfSatisfying(JavafmtBinary.Outcome.Refused.class, r -> assertThat(r.internal()).isTrue());
        assertThat(Duration.between(begun, Instant.now())).isLessThan(TIMEOUT.plusSeconds(2));
    }

    @Test
    @Timeout(10)
    void interruptingTheCallerCancelsPromptlyWithoutThrowing(@TempDir final Path dir) throws Exception {
        final var daemon = start(script(dir, "cat > /dev/null\n"));
        try {
            final var outcome = new AtomicReference<JavafmtBinary.Outcome>();
            final var thrown = new AtomicReference<Throwable>();
            final var worker = new Thread(
                    () -> {
                        try {
                            outcome.set(daemon.format("class A {}"));
                        } catch (final Throwable t) {
                            thrown.set(t);
                        }
                    });
            worker.start();
            Thread.sleep(200);
            worker.interrupt();
            worker.join(Duration.ofSeconds(1).toMillis());
            assertThat(worker.isAlive()).isFalse();
            assertThat(thrown.get()).isNull();
            assertThat(outcome.get()).isEqualTo(new JavafmtBinary.Outcome.Cancelled());
            assertThat(daemon.isAlive()).isTrue();
        } finally {
            daemon.stop();
        }
    }

    @Test
    @Timeout(10)
    void stoppingTheDaemonCancelsTheFormatsInFlight(@TempDir final Path dir) throws Exception {
        final var daemon = start(script(dir, "cat > /dev/null\n"));
        final var inFlight = CompletableFuture.supplyAsync(() -> daemon.format("class A {}"));
        Thread.sleep(200);
        daemon.stop();
        assertThat(inFlight.join()).isEqualTo(new JavafmtBinary.Outcome.Cancelled());
        assertThat(daemon.format("class B {}")).isEqualTo(new JavafmtBinary.Outcome.Cancelled());
    }

    @Test
    @Timeout(10)
    void aDaemonThatCrashesIsRefusedWithoutWaitingForTheTimeout(@TempDir final Path dir) throws Exception {
        final var daemon = JavafmtDaemon.start(script(dir, READ_REQUEST + reply(1, "first") + READ_REQUEST + "exit 3\n"), Duration.ofSeconds(30));
        assertThat(daemon.format("first")).isEqualTo(new JavafmtBinary.Outcome.Formatted("first", List.of()));
        final var begun = Instant.now();
        assertThat(daemon.format("second")).isEqualTo(new JavafmtBinary.Outcome.Refused("javafmt exited with code 3", true));
        assertThat(Duration.between(begun, Instant.now())).isLessThan(Duration.ofSeconds(5));
        assertThat(daemon.isAlive()).isFalse();
        assertThat(daemon.format("third")).isInstanceOf(JavafmtBinary.Outcome.Refused.class);
    }

    @Test
    @Timeout(10)
    void aDaemonThatHasAlreadyExitedIsNotKilledByItsStalePid(@TempDir final Path dir) throws Exception {
        final var killed = new CompletableFuture<Process>();
        final var daemon = JavafmtDaemon.start(script(dir, "exit 3\n"), Duration.ofSeconds(30), killed::complete);
        assertThat(daemon.format("class A {}")).isEqualTo(new JavafmtBinary.Outcome.Refused("javafmt exited with code 3", true));
        daemon.stop();
        Thread.sleep(200);
        assertThat(killed).isNotDone();
    }

    @Test
    @Timeout(10)
    void stoppingALiveDaemonKillsItsProcess(@TempDir final Path dir) throws Exception {
        final var killed = new CompletableFuture<Process>();
        final var daemon = JavafmtDaemon.start(script(dir, "cat > /dev/null\n"), TIMEOUT, killed::complete);
        daemon.stop();
        assertThat(killed).succeedsWithin(Duration.ofSeconds(5)).satisfies(process -> process.destroyForcibly().waitFor());
    }

    @Test
    @Timeout(10)
    void aBinaryWithoutDaemonModeIsRefused(@TempDir final Path dir) throws Exception {
        final var daemon = JavafmtDaemon.start(script(dir, "echo \"Unknown option: '$1'\" >&2\nexit 2\n"), Duration.ofSeconds(30));
        assertThat(daemon.format("class A {}")).isEqualTo(new JavafmtBinary.Outcome.Refused("javafmt exited with code 2", true));
    }

    @Test
    @Timeout(10)
    void aReplyThatIsNotProtobufStopsTheDaemon(@TempDir final Path dir) throws Exception {
        final var daemon = start(script(dir, READ_REQUEST + printf(new byte[] {3, (byte) 0xff, (byte) 0xff, (byte) 0xff,}) + "cat > /dev/null\n"));
        assertThat(daemon.format("class A {}"))
                .isEqualTo(new JavafmtBinary.Outcome.Refused("javafmt sent a reply that is not part of its protocol", true));
        assertThat(daemon.isAlive()).isFalse();
    }

    @Test
    @Timeout(10)
    void aFloodOnStderrDoesNotStallReplies(@TempDir final Path dir) throws Exception {
        final var daemon = start(script(dir, "head -c 5000000 /dev/zero | tr '\\0' 'x' >&2\n" + ECHO_REQUESTS));
        try {
            assertThat(daemon.format("class A {}")).isEqualTo(new JavafmtBinary.Outcome.Formatted("class A {}", List.of()));
        } finally {
            daemon.stop();
        }
    }

    @Test
    @Timeout(20)
    void aSourceLargerThanThePipeBufferRoundTrips(@TempDir final Path dir) throws Exception {
        final var daemon = JavafmtDaemon.start(script(dir, ECHO_REQUESTS), Duration.ofSeconds(15));
        try {
            final var source = "y".repeat(300_000);
            assertThat(daemon.format(source)).isEqualTo(new JavafmtBinary.Outcome.Formatted(source, List.of()));
        } finally {
            daemon.stop();
        }
    }

    @Test
    @Timeout(10)
    void stopEndsTheProcess(@TempDir final Path dir) throws Exception {
        final var pidFile = dir.resolve("pid");
        final var daemon = start(script(dir, "echo $$ > '" + pidFile + "'\n" + ECHO_REQUESTS));
        daemon.format("class A {}");
        daemon.stop();
        assertThat(daemon.isAlive()).isFalse();
        awaitExit(Long.parseLong(Files.readString(pidFile).strip()));
    }

    static void awaitExit(final long pid) throws InterruptedException {
        final var deadline = Instant.now().plusSeconds(5);
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
        }
        assertThat(ProcessHandle.of(pid).filter(ProcessHandle::isAlive)).isEmpty();
    }

    static String reply(final int id, final String output) {
        final var reply = FormatReply.newBuilder().setId(id).setOutput(output).build();
        final var bytes = new ByteArrayOutputStream();
        try {
            reply.writeDelimitedTo(bytes);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return printf(bytes.toByteArray());
    }

    static String printf(final byte[] bytes) {
        return IntStream.range(0, bytes.length)
                .mapToObj(i -> String.format("\\%03o", bytes[i] & 0xff))
                .collect(Collectors.joining("", "printf '", "'\n"));
    }

    static Path script(final Path dir, final String body) throws IOException {
        final var path = dir.resolve("javafmt-fake-daemon");
        Files.writeString(path, "#!/bin/sh\n[ \"$1\" = \"--daemon\" ] || exit 9\n" + body);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
    }

    private static JavafmtDaemon start(final Path executable) throws ExecutionException {
        return JavafmtDaemon.start(executable, TIMEOUT);
    }
}
