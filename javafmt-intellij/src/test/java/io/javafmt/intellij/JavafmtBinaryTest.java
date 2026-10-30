package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.util.EnvironmentUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.LockSupport;
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

    @Test
    void versionReadsTheBinaryVersionString(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\necho 'javafmt 0.2.0'\n");
        assertThat(new JavafmtBinary(fake).version()).isEqualTo(new JavafmtBinary.VersionProbe.Reported("javafmt 0.2.0"));
    }

    @Test
    void aBinaryThatExitsWithAnErrorFailsTheProbe(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\nexit 2\n");
        assertThat(new JavafmtBinary(fake).version()).isEqualTo(new JavafmtBinary.VersionProbe.Failed());
    }

    @Test
    void anInterruptedProbeIsInconclusive(@TempDir final Path dir) throws Exception {
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\nexec sleep 15\n");
        Thread.currentThread().interrupt();
        try {
            assertThat(new JavafmtBinary(fake).version()).isEqualTo(new JavafmtBinary.VersionProbe.Inconclusive());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @Timeout(10)
    void aProbeInterruptedMidRunReturnsPromptlyEvenWhenTheBinaryIgnoresTermination(@TempDir final Path dir) throws Exception {
        final var pidFile = dir.resolve("pid");
        final var fake = fakeBinaryScript(dir, "#!/bin/sh\necho $$ > '" + pidFile + "'\ntrap '' TERM\nwhile :; do sleep 1; done\n");
        final var caller = Thread.currentThread();
        final var interrupter = Thread.ofPlatform()
                .start(() -> {
                    LockSupport.parkNanos(Duration.ofMillis(300).toNanos());
                    caller.interrupt();
                });
        final var started = Instant.now();
        try {
            assertThat(new JavafmtBinary(fake).version()).isEqualTo(new JavafmtBinary.VersionProbe.Inconclusive());
            assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(2));
        } finally {
            Thread.interrupted();
            interrupter.join();
        }
        ProcessHandle.of(Long.parseLong(Files.readString(pidFile).strip()))
                .ifPresent(script -> assertThat(script.onExit()).succeedsWithin(Duration.ofSeconds(5)));
    }

    @Test
    void aBinaryThatCannotBeStartedFailsTheProbe(@TempDir final Path dir) {
        assertThat(new JavafmtBinary(dir.resolve("absent")).version()).isEqualTo(new JavafmtBinary.VersionProbe.Failed());
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

    private static Path fakeBinaryScript(final Path dir, final String script) throws IOException {
        final var path = dir.resolve("javafmt-fake");
        Files.writeString(path, script);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
    }
}
