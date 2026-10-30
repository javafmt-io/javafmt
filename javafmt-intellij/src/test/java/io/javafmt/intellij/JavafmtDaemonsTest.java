package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.MAC, OS.LINUX})
@Timeout(20)
class JavafmtDaemonsTest {

    private final JavafmtDaemons daemons = new JavafmtDaemons();

    @Test
    void oneProcessServesEveryFormatWithTheSameBinary(@TempDir final Path dir) throws Exception {
        final var starts = dir.resolve("starts");
        final var binary = identity(JavafmtDaemonTest.script(dir, "echo started >> '" + starts + "'\n" + JavafmtDaemonTest.ECHO_REQUESTS));
        try {
            assertThat(List.of(daemons.format(binary, "a"), daemons.format(binary, "b"), daemons.format(binary, "c")))
                    .containsExactly(
                            new JavafmtBinary.Outcome.Formatted("a", List.of()),
                            new JavafmtBinary.Outcome.Formatted("b", List.of()),
                            new JavafmtBinary.Outcome.Formatted("c", List.of()));
            assertThat(Files.readAllLines(starts)).hasSize(1);
        } finally {
            daemons.dispose();
        }
    }

    @Test
    void aCrashedDaemonIsReplacedOnTheNextFormat(@TempDir final Path dir) throws Exception {
        final var crashed = dir.resolve("crashed");
        final var binary = identity(
                JavafmtDaemonTest.script(
                        dir,
                        "if [ ! -f '" + crashed + "' ]; then\n    touch '" + crashed + "'\n" + JavafmtDaemonTest.READ_REQUEST + "    exit 3\nfi\n"
                                + JavafmtDaemonTest.ECHO_REQUESTS));
        try {
            assertThat(daemons.format(binary, "a")).isEqualTo(new JavafmtBinary.Outcome.Refused("javafmt exited with code 3", true));
            assertThat(daemons.format(binary, "b")).isEqualTo(new JavafmtBinary.Outcome.Formatted("b", List.of()));
        } finally {
            daemons.dispose();
        }
    }

    @Test
    void aReplacedBinaryGetsAFreshDaemonAndTheOldOneIsStopped(@TempDir final Path dir) throws Exception {
        final var pidFile = dir.resolve("pid");
        final var path = JavafmtDaemonTest.script(dir, "echo $$ > '" + pidFile + "'\n" + JavafmtDaemonTest.ECHO_REQUESTS);
        try {
            assertThat(daemons.format(identity(path), "a")).isInstanceOf(JavafmtBinary.Outcome.Formatted.class);
            final var firstPid = Long.parseLong(Files.readString(pidFile).strip());
            JavafmtDaemonTest.script(
                    dir,
                    "echo $$ > '" + pidFile + "'\n" + JavafmtDaemonTest.READ_REQUEST + JavafmtDaemonTest.reply(1, "replaced b")
                            + "cat > /dev/null\n");
            assertThat(daemons.format(identity(path), "b")).isEqualTo(new JavafmtBinary.Outcome.Formatted("replaced b", List.of()));
            JavafmtDaemonTest.awaitExit(firstPid);
        } finally {
            daemons.dispose();
        }
    }

    @Test
    void disposeStopsTheDaemon(@TempDir final Path dir) throws Exception {
        final var pidFile = dir.resolve("pid");
        final var binary = identity(JavafmtDaemonTest.script(dir, "echo $$ > '" + pidFile + "'\n" + JavafmtDaemonTest.ECHO_REQUESTS));
        daemons.format(binary, "a");
        daemons.dispose();
        JavafmtDaemonTest.awaitExit(Long.parseLong(Files.readString(pidFile).strip()));
    }

    @Test
    void noDaemonStartsOnceDisposed(@TempDir final Path dir) throws Exception {
        final var starts = dir.resolve("starts");
        final var binary = identity(JavafmtDaemonTest.script(dir, "echo started >> '" + starts + "'\n" + JavafmtDaemonTest.ECHO_REQUESTS));
        daemons.dispose();
        assertThat(daemons.format(binary, "a")).isEqualTo(new JavafmtBinary.Outcome.Cancelled());
        assertThat(starts).doesNotExist();
    }

    @Test
    void aFormatInFlightWhenTheBinaryIsSwappedIsCancelledRatherThanFailed(@TempDir final Path dir) throws Exception {
        final var slow = identity(JavafmtDaemonTest.script(Files.createDirectory(dir.resolve("slow")), "cat > /dev/null\n"));
        final var fast = identity(JavafmtDaemonTest.script(Files.createDirectory(dir.resolve("fast")), JavafmtDaemonTest.ECHO_REQUESTS));
        try {
            final var inFlight = CompletableFuture.supplyAsync(() -> daemons.format(slow, "a"));
            Thread.sleep(200);
            assertThat(daemons.format(fast, "b")).isEqualTo(new JavafmtBinary.Outcome.Formatted("b", List.of()));
            assertThat(inFlight.join()).isEqualTo(new JavafmtBinary.Outcome.Cancelled());
        } finally {
            daemons.dispose();
        }
    }

    @Test
    void aBinaryThatCannotBeStartedIsRefusedAsTheUsersProblem(@TempDir final Path dir) throws Exception {
        final var notExecutable = dir.resolve("javafmt");
        Files.writeString(notExecutable, "not a program");
        assertThat(daemons.format(identity(notExecutable), "a"))
                .isInstanceOfSatisfying(JavafmtBinary.Outcome.Refused.class, refused -> assertThat(refused.internal()).isFalse());
    }

    private static BinaryIdentity identity(final Path path) {
        return BinaryIdentity.of(path).orElseThrow();
    }
}
