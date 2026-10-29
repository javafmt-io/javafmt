package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.common.ThreadLeakTracker;
import com.intellij.util.EnvironmentUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// The fake binaries are /bin/sh scripts with POSIX permissions. Windows spawn behaviour is a
// listed risk to measure on real hardware, not something to fake here.
@EnabledOnOs({OS.MAC, OS.LINUX})
class JavafmtBinaryTest {

    /**
     * Scope for the {@code ThreadLeakTracker} exemption a couple of tests below register for
     * threads that are expected to deliberately outlive the test method (see those tests for why).
     * {@code longRunningThreadCreated} adds names to a JVM-static ignore set and only removes them
     * once this Disposable is disposed — {@code :javafmt-intellij:test} runs the whole module in a
     * single JVM with no per-class forking, so an exemption that is never disposed would silently
     * blind the leak tracker to any later test's same-named thread for the rest of the run. Each
     * test gets its own instance of this field (JUnit5's default per-method lifecycle), so disposal
     * here is scoped to exactly the test that registered it.
     */
    private Disposable threadLeakExemptionScope;

    @AfterEach
    void disposeThreadLeakExemptionScope() {
        if (threadLeakExemptionScope != null) {
            Disposer.dispose(threadLeakExemptionScope);
        }
    }

    @Test
    void formatsBySpawningTheBinaryAndReadingStdout(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinary(dir, "cat", "");
        final var outcome = new JavafmtBinary(fake).format("class A {}\n", List.of());
        assertThat(outcome)
                .isInstanceOfSatisfying(
                        JavafmtBinary.Outcome.Formatted.class,
                        f -> {
                            assertThat(f.output()).isEqualTo("class A {}\n");
                            assertThat(f.diagnostics()).isEmpty();
                        });
    }

    @Test
    void collectsNdjsonDiagnosticsFromStderr(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinary(dir, "cat", "{\"kind\":\"warning\",\"message\":\"m\",\"line\":1,\"col\":1}");
        final var outcome = new JavafmtBinary(fake).format("class A {}\n", List.of());
        assertThat(outcome)
                .isInstanceOfSatisfying(
                        JavafmtBinary.Outcome.Formatted.class,
                        f -> assertThat(f.diagnostics())
                                .singleElement()
                                .satisfies(d -> assertThat(d.kind()).isEqualTo("warning")));
    }

    @Test
    void skipsMalformedStderrLinesWithoutFailingTheFormat(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinary(dir, "cat", "not json\n{\"kind\":\"warning\",\"message\":\"m\",\"line\":1,\"col\":1}");
        final var outcome = new JavafmtBinary(fake).format("class A {}\n", List.of());
        assertThat(outcome).isInstanceOfSatisfying(JavafmtBinary.Outcome.Formatted.class, f -> assertThat(f.diagnostics()).hasSize(1));
    }

    @Test
    void nonZeroExitWithNoDiagnosticsIsAnInternalRefusal(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\nexit 3\n");
        final var outcome = new JavafmtBinary(fake).format("class A {}\n", List.of());
        assertThat(outcome).isInstanceOfSatisfying(JavafmtBinary.Outcome.Refused.class, r -> assertThat(r.internal()).isTrue());
    }

    @Test
    void missingExecutableRefusesRatherThanThrowing(@TempDir final Path dir) {
        final var outcome = new JavafmtBinary(dir.resolve("nope")).format("class A {}\n", List.of());
        assertThat(outcome).isInstanceOf(JavafmtBinary.Outcome.Refused.class);
    }

    @Test
    void passesFlagsThrough(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\necho \"$@\"\n");
        final var outcome = new JavafmtBinary(fake).format("", List.of("--check"));
        assertThat(outcome)
                .isInstanceOfSatisfying(
                        JavafmtBinary.Outcome.Formatted.class,
                        f -> assertThat(f.output())
                                .contains("--check")
                                .contains("--diagnostics=json"));
    }

    @Test
    void versionReadsTheBinaryVersionString(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\necho 'javafmt 0.2.0'\n");
        assertThat(new JavafmtBinary(fake).version()).contains("javafmt 0.2.0");
    }

    @Test
    void resolveFindsAConfiguredExecutable(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\nexit 0\n");
        assertThat(JavafmtBinary.resolve(fake.toString())).contains(fake);
    }

    @Test
    void resolveRejectsAConfiguredPathThatIsNotAFile(@TempDir final Path dir) {
        assertThat(JavafmtBinary.resolve(dir.resolve("absent").toString())).isEmpty();
    }

    @Test
    void theSearchedPathIsTheIdesRatherThanTheJvmsOwnEnvironment() {
        // An IDE started from the macOS Dock, Spotlight or the Toolbox App inherits launchd's
        // minimal environment: no /opt/homebrew/bin, no /usr/local/bin, no ~/.sdkman. Reading
        // System.getenv("PATH") there finds nothing, while the same javafmt works in a terminal.
        // EnvironmentUtil re-reads the login shell's environment, which is where it actually lives.
        assertThat(JavafmtBinary.searchPath()).isEqualTo(EnvironmentUtil.getValue("PATH"));
    }

    @Test
    void resolveFindsAnExecutableOnAPathTheJvmsEnvironmentDoesNotHave(@TempDir final Path dir) throws Exception {
        final var fake = dir.resolve("javafmt");
        Files.writeString(fake, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(fake, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThat(System.getenv("PATH")).doesNotContain(dir.toString());
        assertThat(JavafmtBinary.resolveOnPath(dir.toString())).contains(fake);
    }

    @Test
    void resolveIgnoresANonExecutableJavafmtOnThePath(@TempDir final Path dir) throws Exception {
        final var notExecutable = dir.resolve("javafmt");
        Files.writeString(notExecutable, "not a program");
        Files.setPosixFilePermissions(notExecutable, PosixFilePermissions.fromString("rw-r--r--"));
        assertThat(JavafmtBinary.resolveOnPath(dir.toString())).isEmpty();
    }

    @Test
    void resolveOnAnAbsentPathFindsNothing() {
        assertThat(JavafmtBinary.resolveOnPath(null)).isEmpty();
    }

    @Test
    void theGateAcceptsABinaryReportingThePluginsOwnBuildVersion() {
        // A plugin that rejects the binary released alongside it never formats anything, so the
        // gate is pinned to the version the build stamps rather than to a hand-maintained floor.
        final var buildVersion = System.getProperty("javafmt.buildVersion");
        assertThat(buildVersion).isNotBlank();
        assertThat(JavafmtBinary.isSupportedVersion("javafmt " + buildVersion)).isTrue();
    }

    // Two components ("javafmt 1.0"), four, and any suffix all have to parse: the gate reads the
    // version of a binary it does not control the numbering of.
    @ParameterizedTest
    @ValueSource(strings = {"javafmt 99.0", "javafmt 99.0.0", "javafmt 99.0.0.1", "javafmt 99.1.2-rc1", "javafmt 99.0.0+build.7"})
    void versionsAboveThePluginsOwnAreAccepted(final String versionOutput) {
        assertThat(JavafmtBinary.isSupportedVersion(versionOutput)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"javafmt 0.0", "javafmt 0.0.9", "javafmt 0.0.9-SNAPSHOT"})
    void versionsBelowThePluginsOwnAreRejected(final String versionOutput) {
        assertThat(JavafmtBinary.isSupportedVersion(versionOutput)).isFalse();
    }

    @Test
    void unparseableVersionStringsAreRejectedRatherThanAssumedGood() {
        assertThat(JavafmtBinary.isSupportedVersion("some other tool")).isFalse();
        assertThat(JavafmtBinary.isSupportedVersion("")).isFalse();
    }

    /**
     * A naive implementation that reads stdout to completion before touching stderr (or vice
     * versa) deadlocks here: the child fills a pipe's OS buffer (tens of KB) writing diagnostics
     * before it ever touches stdout, so a caller stuck fully draining stdout first never gets
     * around to draining the stderr the child is blocked writing into. If format() drains both
     * streams concurrently, this returns almost immediately; if it deadlocks, it hangs for the
     * full 10s timeout and this test's own timeout fires first.
     */
    @Test
    @Timeout(5)
    void drainsStdoutAndStderrConcurrentlySoNeitherPipeFillingBlocksTheOther(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\nhead -c 5000000 /dev/zero | tr '\\0' 'x' >&2\ncat > /dev/null\necho done\n");
        final var start = Instant.now();
        final var outcome = new JavafmtBinary(fake).format("class A {}\n", List.of());
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(3));
        assertThat(outcome).isInstanceOfSatisfying(JavafmtBinary.Outcome.Formatted.class, f -> assertThat(f.output()).isEqualTo("done\n"));
    }

    /**
     * A naive implementation that writes the whole source to stdin before reading any output
     * deadlocks here: the child emits a large chunk to stdout without first draining stdin, so its
     * stdout pipe fills and it blocks writing, while a caller stuck writing all of a large stdin
     * first is blocked on a child that is not reading. If the stdin write happens on its own
     * thread concurrently with reading stdout, this returns quickly regardless of ordering.
     */
    @Test
    @Timeout(5)
    void writesStdinConcurrentlyWithReadingOutputSoNeitherPipeFillingBlocksTheOther(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\nhead -c 5000000 /dev/zero | tr '\\0' 'x'\ncat > /dev/null\n");
        final var largeSource = "x".repeat(5_000_000);
        final var start = Instant.now();
        final var outcome = new JavafmtBinary(fake).format(largeSource, List.of());
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(3));
        assertThat(outcome).isInstanceOfSatisfying(JavafmtBinary.Outcome.Formatted.class, f -> assertThat(f.output()).hasSize(5_000_000));
    }

    /**
     * The child exits immediately without reading stdin. Writing a large source into its stdin
     * pipe then hits a broken pipe partway through (the child is gone and its read end is closed).
     * The write happens on a background thread that swallows the IOException; format() must still
     * return normally rather than propagating it.
     */
    @Test
    void brokenPipeWhileWritingStdinDoesNotThrow(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\nexit 0\n");
        final var largeSource = "x".repeat(5_000_000);
        assertThat(new JavafmtBinary(fake).format(largeSource, List.of())).isInstanceOf(JavafmtBinary.Outcome.class);
    }

    @Test
    void executableThatIsADirectoryRefusesRatherThanThrowing(@TempDir final Path dir) throws Exception {
        final var asDir = dir.resolve("javafmt-dir");
        Files.createDirectory(asDir);
        assertThat(new JavafmtBinary(asDir).format("class A {}\n", List.of())).isInstanceOf(JavafmtBinary.Outcome.Refused.class);
    }

    @Test
    void executableThatIsNotExecutableRefusesRatherThanThrowing(@TempDir final Path dir) throws Exception {
        final var notExecutable = dir.resolve("javafmt-not-exec");
        Files.writeString(notExecutable, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(notExecutable, PosixFilePermissions.fromString("rw-r--r--"));
        assertThat(new JavafmtBinary(notExecutable).format("class A {}\n", List.of())).isInstanceOf(JavafmtBinary.Outcome.Refused.class);
    }

    /** Confirms the 10s hang timeout actually fires and destroys the process rather than blocking forever. */
    @Test
    @Timeout(15)
    void aHangingProcessIsRefusedAfterTheTimeoutInsteadOfBlockingForever(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\nexec sleep 60\n");
        final var outcome = new JavafmtBinary(fake).format("class A {}\n", List.of());
        assertThat(outcome).isInstanceOfSatisfying(JavafmtBinary.Outcome.Refused.class, r -> assertThat(r.internal()).isTrue());
    }

    /**
     * Interrupting the calling thread mid-format must unblock it well before the 10s hang timeout
     * and must not surface as an uncaught exception on that thread.
     */
    @Test
    @Timeout(5)
    void interruptingTheCallingThreadRefusesPromptlyWithoutThrowing(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\nexec sleep 60\n");
        final var outcomeHolder = new JavafmtBinary.Outcome[1];
        final var thrown = new Throwable[1];
        final var worker = new Thread(
                () -> {
                    try {
                        outcomeHolder[0] = new JavafmtBinary(fake).format("class A {}\n", List.of());
                    } catch (final Throwable t) {
                        thrown[0] = t;
                    }
                });
        worker.start();
        Thread.sleep(200);
        worker.interrupt();
        worker.join(Duration.ofSeconds(3).toMillis());
        assertThat(worker.isAlive()).isFalse();
        assertThat(thrown[0]).isNull();
        assertThat(outcomeHolder[0]).isInstanceOf(JavafmtBinary.Outcome.Refused.class);
    }

    /**
     * Regression test for IMPORTANT 1: {@code Process.destroyForcibly} is not safe to call on the
     * calling thread. When the child never drains stdin and the source exceeds the OS pipe buffer,
     * the stdin-writer thread parks inside a blocking, non-interruptible native {@code write()}
     * while holding the monitor on the stream it is writing to. The JDK's own destroy path needs
     * that identical monitor to close and flush the same stream, and nothing else is left to send
     * the kill signal that would release the writer — confirmed with {@code jstack} to block the
     * calling thread indefinitely, not just slowly, in this exact shape. Before the fix, the
     * timeout path called {@code destroyForcibly()} synchronously, so this scenario blocked
     * {@code format()} itself far past the stated 10s timeout. The fix runs the kill and cleanup on
     * a detached thread, so the calling thread's bound no longer depends on it ever finishing.
     *
     * <p>The fake binary sleeps comfortably longer than the 10s timeout but not for hours, so if the
     * background cleanup thread's {@code destroyForcibly()} call does hang (as the real defect
     * does), the orphaned child still exits on its own a few seconds later instead of lingering
     * indefinitely.
     */
    @Test
    @Timeout(15)
    void aChildThatNeverDrainsStdinDoesNotBlockFormatPastItsOwnTimeout(@TempDir final Path dir) throws Exception {
        // This scenario deliberately leaves all four helper threads (the stdin writer, the two
        // drains, and the destroyForcibly() cleanup thread) alive past the end of the test: none of
        // them unblock until the fake child's own sleep elapses, well after format() has already
        // returned. Tell the platform's thread-leak tracker not to flag them, rather than have the
        // test wait out that unrelated tail latency just to satisfy an unrelated test-framework
        // check — scoped to this test via threadLeakExemptionScope, disposed in @AfterEach, so the
        // exemption does not leak into later tests in the same JVM.
        threadLeakExemptionScope = () -> {};
        ThreadLeakTracker.longRunningThreadCreated(
                threadLeakExemptionScope,
                "javafmt-stdin-writer",
                "javafmt-stdout-reader",
                "javafmt-stderr-reader",
                "javafmt-cleanup");
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\nexec sleep 15\n");
        final var largeSource = "x".repeat(1_000_000);
        final var start = Instant.now();
        final var outcome = new JavafmtBinary(fake).format(largeSource, List.of());
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(13));
        assertThat(outcome).isInstanceOfSatisfying(JavafmtBinary.Outcome.Refused.class, r -> assertThat(r.internal()).isTrue());
    }

    /**
     * Same defect as above, reproduced on the waitFor-interrupted path instead of the timeout path:
     * the reviewer found it needs neither the full 10s wait nor a multi-megabyte source to trigger
     * — 300KB and an interrupt at 200ms was enough, because the writer thread only needs to have
     * filled the OS pipe buffer (a few tens of KB) and parked in {@code write()} by the time
     * {@code destroyForcibly()} is attempted.
     */
    @Test
    @Timeout(5)
    void interruptingDuringWaitForDoesNotBlockOnDestroyingAChildThatNeverDrainsStdin(@TempDir final Path dir) throws Exception {
        // Same reasoning as the timeout-path test above: all four helper threads are expected to
        // outlive the test method, and the exemption is scoped via threadLeakExemptionScope.
        threadLeakExemptionScope = () -> {};
        ThreadLeakTracker.longRunningThreadCreated(
                threadLeakExemptionScope,
                "javafmt-stdin-writer",
                "javafmt-stdout-reader",
                "javafmt-stderr-reader",
                "javafmt-cleanup");
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\nexec sleep 15\n");
        final var largeSource = "x".repeat(300_000);
        final var outcomeHolder = new JavafmtBinary.Outcome[1];
        final var thrown = new Throwable[1];
        final var worker = new Thread(
                () -> {
                    try {
                        outcomeHolder[0] = new JavafmtBinary(fake).format(largeSource, List.of());
                    } catch (final Throwable t) {
                        thrown[0] = t;
                    }
                });
        worker.start();
        Thread.sleep(200);
        worker.interrupt();
        worker.join(Duration.ofSeconds(3).toMillis());
        assertThat(worker.isAlive()).isFalse();
        assertThat(thrown[0]).isNull();
        assertThat(outcomeHolder[0]).isInstanceOf(JavafmtBinary.Outcome.Refused.class);
    }

    /**
     * Proves the {@code ThreadLeakTracker} exemption used by the two tests above does not outlive
     * its {@code Disposable}: {@code longRunningThreadCreated} adds names to a JVM-static ignore
     * set and only removes them when the given Disposable is disposed, so a throwaway Disposable
     * that is never disposed would exempt those thread names for the rest of the test JVM's life —
     * a real risk here, since {@code isWellKnownOffender} matches by substring
     * ({@code thread.getName().contains(offender)}) and {@code :javafmt-intellij:test} runs the
     * whole module in one JVM with no per-class forking. This registers and disposes its own scope
     * directly against the real {@code ThreadLeakTracker}, independent of {@code JavafmtBinary},
     * and confirms a thread whose name matches an already-disposed exemption is flagged as a leak
     * exactly as if it had never been exempted.
     */
    @Test
    @Timeout(30)
    void threadLeakExemptionDoesNotOutliveItsDisposable() throws Exception {
        final var name = "javafmt-binary-test-scoped-exemption-probe";
        final var scope = (Disposable) () -> {};
        ThreadLeakTracker.longRunningThreadCreated(scope, name);
        Disposer.dispose(scope);
        // checkLeak(before) flags threads alive now that were not in the "before" snapshot, so the
        // snapshot must be taken before the probe thread starts, not after.
        final var before = ThreadLeakTracker.getThreads();
        final var latch = new CountDownLatch(1);
        final var thread = new Thread(
                () -> {
                    try {
                        latch.await();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                name);
        thread.setDaemon(true);
        thread.start();
        try {
            assertThatThrownBy(() -> ThreadLeakTracker.checkLeak(before)).isInstanceOf(AssertionError.class);
        } finally {
            latch.countDown();
            thread.join(Duration.ofSeconds(5).toMillis());
        }
    }

    /**
     * Regression test for the interrupt cascade in {@code collectAfterExit}. {@code Thread.join}
     * only throws {@code InterruptedException} when it actually has to block on a still-live
     * thread; joining an already-dead thread returns immediately regardless of the interrupt flag.
     * So to reproduce the cascade deterministically — rather than racing a real process's exit
     * against a real interrupt, which only landed the bad case in roughly 1% of trials — this
     * sets the calling thread's interrupt flag up front and gives {@code collectAfterExit} a drain
     * that is genuinely still reading (a pipe whose write end is deliberately left open, so
     * {@code readAllBytes()} never sees EOF). That guarantees the stdout join has to actually
     * block, so the pre-set flag fires inside it for real, and the return value of every join
     * after it is provably unusable rather than a call that happened to return instantly.
     *
     * <p>Before this fix, {@code collectAfterExit}'s predecessor caught that
     * {@code InterruptedException}, re-armed the flag, and carried on regardless — producing an
     * {@code Outcome.Formatted} built from whatever partial bytes the stalled drain had captured so
     * far (here, none of them). That is the silent-data-loss failure mode this test exists to
     * catch: it must never resolve to {@code Formatted} with less output than the child actually
     * wrote, only to {@code Refused}.
     */
    @Test
    @Timeout(5)
    void anInterruptedJoinDuringOutputCollectionRefusesInsteadOfReportingTruncatedOutput() throws Exception {
        final var neverClosedStdout = new PipedInputStream();
        final var keepStdoutOpen = new PipedOutputStream(neverClosedStdout);
        keepStdoutOpen.write("only some of the out".getBytes(StandardCharsets.UTF_8));
        keepStdoutOpen.flush();
        final var stalledStdoutDrain = new JavafmtBinary.StreamDrain(neverClosedStdout, "test-stalled-stdout-drain");
        final var finishedStderrDrain = new JavafmtBinary.StreamDrain(new ByteArrayInputStream(new byte[0]), "test-finished-stderr-drain");
        final var alreadyFinishedStdinWriter = new Thread(() -> {});
        alreadyFinishedStdinWriter.start();
        alreadyFinishedStdinWriter.join();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        Thread.currentThread().interrupt();
        try {
            final var outcome = JavafmtBinary.collectAfterExit(
                    0,
                    alreadyFinishedStdinWriter,
                    stalledStdoutDrain,
                    finishedStderrDrain,
                    Instant.now().plusSeconds(2));
            // The regression this guards against: collectAfterExit's predecessor would land here as
            // Outcome.Formatted("", List.of()) — 0 bytes instead of the child's real output — because
            // the interrupted join was silently treated as "done". It must be Refused instead.
            assertThat(outcome).isInstanceOf(JavafmtBinary.Outcome.Refused.class);
        } finally {
            // The interrupt flag must survive for the caller rather than being silently absorbed.
            assertThat(Thread.interrupted()).isTrue();
            keepStdoutOpen.close();
        }
    }

    /** Writes a shell script that echoes stdin through `cat` and optionally prints NDJSON to stderr. */
    private static Path fakeBinary(final Path dir, final String body, final String stderr) throws IOException {
        final var script = stderr.isEmpty() ? "#!/bin/sh\n" + body + "\n" : "#!/bin/sh\nprintf '%s\\n' '" + stderr + "' >&2\n" + body + "\n";
        return fakeBinaryScript(dir, script);
    }

    private static Path fakeBinaryScript(final Path dir, final String script) throws IOException {
        final var path = dir.resolve("javafmt-fake");
        Files.writeString(path, script);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
    }
}
