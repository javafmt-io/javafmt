package io.javafmt.intellij;

import com.intellij.execution.process.ProcessOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class VersionProbeTest {
    private static final Duration SHORT = Duration.ofMillis(20);

    private final AtomicBoolean destroyed = new AtomicBoolean();

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void reportsWhatTheBinaryPrinted() {
        assertThat(await(CompletableFuture.completedFuture(new ProcessOutput("javafmt 1.2.3\n", "", 0, false, false))))
                .isEqualTo(new JavafmtBinary.VersionProbe.Reported("javafmt 1.2.3"));
    }

    @Test
    void aNonZeroExitFails() {
        assertThat(await(CompletableFuture.completedFuture(new ProcessOutput("", "unknown option", 2, false, false))))
                .isInstanceOf(JavafmtBinary.VersionProbe.Failed.class);
    }

    @Test
    void aProcessThatTimedOutIsInconclusive() {
        assertThat(await(CompletableFuture.completedFuture(new ProcessOutput("", "", -1, true, false))))
                .isInstanceOf(JavafmtBinary.VersionProbe.Inconclusive.class);
    }

    @Test
    void aProbeThatNeverAnswersIsInconclusive() {
        assertThat(await(new CompletableFuture<>())).isInstanceOf(JavafmtBinary.VersionProbe.Inconclusive.class);
    }

    @Test
    void aProbeThatThrowsFails() {
        assertThat(await(CompletableFuture.failedFuture(new IllegalStateException("could not start"))))
                .isInstanceOf(JavafmtBinary.VersionProbe.Failed.class);
    }

    @Test
    void anInterruptedWaitIsInconclusiveKillsTheProcessAndKeepsTheInterrupt() {
        Thread.currentThread().interrupt();
        assertThat(await(new CompletableFuture<>())).isInstanceOf(JavafmtBinary.VersionProbe.Inconclusive.class);
        assertThat(destroyed).isTrue();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private JavafmtBinary.VersionProbe await(final CompletableFuture<ProcessOutput> run) {
        return JavafmtBinary.await(run, SHORT, () -> destroyed.set(true));
    }
}
