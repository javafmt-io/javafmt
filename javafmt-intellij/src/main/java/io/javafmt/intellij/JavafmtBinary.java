package io.javafmt.intellij;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Runs the native {@code javafmt} binary out of process.
 *
 * <p>Out of process is what lets this module compile to Java 21 bytecode while the formatter stays
 * on Java 25: the binary embeds its own runtime, so {@code javafmt-core} is never loaded by the
 * IDE's JBR. Measured cold start is ~6 ms, which is free against Reformat Code's perceptual budget,
 * so there is no daemon to manage.
 *
 * <p>Writing to the child's stdin and reading its stdout/stderr all happen on separate threads
 * running concurrently, not sequentially on the caller's thread. A pipe's OS buffer is a few tens
 * of KB; javafmt's stdout carries the whole formatted file and its stderr can carry one NDJSON
 * diagnostic per warning, so either can exceed that on a large or lint-heavy file. Reading stdout
 * to completion before touching stderr (or vice versa) risks the classic {@link ProcessBuilder}
 * deadlock: the child blocks writing to whichever stream fills first, while this thread is blocked
 * reading the other one and never gets to it. The same trap applies in reverse to stdin: writing
 * the whole source before reading any output can deadlock against a child that starts emitting
 * output before it has consumed all of its input.
 *
 * <p><b>{@link #format} returns within roughly {@code TIMEOUT_SECONDS} (10s) of being called,
 * regardless of what the child process or its helper threads do afterward.</b> That bound holds
 * even when killing the process would itself hang: the JDK's own {@code Process.destroyForcibly}
 * needs the same monitor that the stdin-writer thread holds while parked in a blocking,
 * non-interruptible native {@code write()} call — observed (via {@code jstack}) to deadlock
 * permanently when the child never drains stdin and the source exceeds the OS pipe buffer, because
 * nothing else is left to send the kill signal that would release the writer. Killing the process
 * and cleaning up its helper threads therefore never happens on the calling thread; it runs on a
 * detached daemon thread that may itself leak until the child eventually exits on its own. That is
 * an acceptable outcome — a stray daemon thread against a process the OS will eventually reap is far
 * cheaper than freezing every caller of {@code format} against one stuck binary.
 */
public final class JavafmtBinary {

    /** Real work is ~10 ms; this only ever catches a hang. */
    private static final long TIMEOUT_SECONDS = 10;

    /**
     * Grace period the detached cleanup thread gives the helper threads to notice a destroyed
     * process and exit. Purely housekeeping on a thread nobody is waiting on: it does not affect
     * {@link #format}'s own bound, which is set entirely by {@code TIMEOUT_SECONDS} above.
     */
    private static final long CLEANUP_GRACE_SECONDS = 2;

    /**
     * A {@code javafmt --version} line: the name, then two to four dot-separated numbers, then
     * anything (a {@code -SNAPSHOT} or {@code -rc1} suffix, build metadata, a commit hash). Only
     * the numbers are compared, so a snapshot of a release counts as that release.
     */
    private static final Pattern VERSION = Pattern.compile("javafmt (\\d+(?:\\.\\d+){1,3})");

    /**
     * The plugin's own version, parsed once. A {@code -SNAPSHOT} or {@code -rc} suffix is dropped
     * before comparing, so a snapshot build of the plugin accepts the matching release binary.
     */
    private static final String MINIMUM_VERSION_TEXT = JavafmtPluginVersion.VERSION.split("-")[0];

    private static final List<Integer> MINIMUM_VERSION = components(MINIMUM_VERSION_TEXT);

    private final Path executable;

    public JavafmtBinary(final Path executable) {
        this.executable = Objects.requireNonNull(executable, "executable");
    }

    /** The configured path when it is an executable file, else the first {@code javafmt} on the IDE's PATH. */
    public static Optional<Path> resolve(final String configuredPath) {
        Objects.requireNonNull(configuredPath, "configuredPath");
        if (!configuredPath.isBlank()) {
            final var configured = Path.of(configuredPath);
            return Files.isExecutable(configured) && Files.isRegularFile(configured) ? Optional.of(configured) : Optional.empty();
        }
        return resolveOnPath(searchPath());
    }

    /**
     * The PATH to search, as the IDE understands it rather than as this JVM inherited it.
     *
     * <p>An IDE launched from the macOS Dock, from Spotlight or from the Toolbox App inherits
     * launchd's minimal environment, which has no {@code /opt/homebrew/bin}, no
     * {@code /usr/local/bin} and no {@code ~/.sdkman/candidates}. {@code System.getenv("PATH")}
     * there finds nothing, and the plugin reports "no javafmt binary found" for a javafmt that
     * works fine in the user's terminal. {@code EnvironmentUtil}, which this reads through, exists
     * to paper over exactly that: it loads the login shell's environment.
     */
    static String searchPath() {
        return PathEnvironmentVariableUtil.getPathVariableValue();
    }

    /**
     * The first executable {@code javafmt} on {@code pathVariableValue}, or empty when there is
     * none. Package-private so a test can search a PATH of its own rather than the machine's.
     */
    static Optional<Path> resolveOnPath(final String pathVariableValue) {
        if (pathVariableValue == null || pathVariableValue.isBlank()) {
            return Optional.empty();
        }
        return Stream.of("javafmt", "javafmt.exe")
                .flatMap(name -> Optional.ofNullable(PathEnvironmentVariableUtil.findInPath(name, pathVariableValue, null)).stream())
                .map(File::toPath)
                .filter(candidate -> Files.isExecutable(candidate) && Files.isRegularFile(candidate))
                .findFirst();
    }

    public Outcome format(final String source, final List<String> flags) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(flags, "flags");
        final var command = new ArrayList<String>();
        command.add(executable.toString());
        command.add("--diagnostics=json");
        command.addAll(flags);
        final Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (final IOException e) {
            return new Outcome.Refused("could not run " + executable + ": " + e.getMessage(), false);
        }
        // Fixed at the start of the call so every wait below — waitFor and the joins after it —
        // draws from the same 10s budget rather than each getting its own fresh allowance.
        final var deadline = Instant.now().plusSeconds(TIMEOUT_SECONDS);
        final var stdoutReader = new StreamDrain(process.getInputStream(), "javafmt-stdout-reader");
        final var stderrReader = new StreamDrain(process.getErrorStream(), "javafmt-stderr-reader");
        final var stdinWriter = writeStdinAsync(process, source);
        final boolean finished;
        try {
            finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyAndCleanupInBackground(process, stdinWriter, stdoutReader, stderrReader);
            return new Outcome.Refused("interrupted while running javafmt", true);
        }
        if (!finished) {
            destroyAndCleanupInBackground(process, stdinWriter, stdoutReader, stderrReader);
            return new Outcome.Refused("javafmt did not respond within " + TIMEOUT_SECONDS + "s", true);
        }
        return collectAfterExit(process.exitValue(), stdinWriter, stdoutReader, stderrReader, deadline);
    }

    /**
     * Decides the {@link Outcome} once the process has exited, given the background threads that
     * were writing its stdin and draining its stdout/stderr.
     *
     * <p>{@code Thread.join} is itself interruptible, and an interrupt sets a sticky flag: once one
     * join throws, every join called afterward — even against a thread that finished cleanly ages
     * ago — throws immediately too, without actually checking whether that thread is done. The
     * naive fix of catching and continuing past that is exactly the bug this method exists to
     * avoid: it would go on to build a {@link Outcome.Formatted} from whichever drain threads
     * happened to be interrupted mid-read, silently reporting a truncated capture as a complete,
     * successful format. Every join here is therefore required to genuinely finish — each one
     * reports whether it did — and any join that didn't (interrupted mid-wait, or still running
     * past {@code deadline}) forces a refusal instead.
     *
     * <p>Package-private so a stalled drain (one still mid-read when its join is attempted) can be
     * injected directly in a test, deterministically reproducing the one-interrupt-poisons-every-
     * later-join failure mode without racing a real timing window against a real subprocess.
     */
    static Outcome collectAfterExit(
            final int exitValue,
            final Thread stdinWriter,
            final StreamDrain stdoutReader,
            final StreamDrain stderrReader,
            final Instant deadline) {
        final var stdinDone = awaitCompletion(stdinWriter, deadline);
        final var stdoutDone = stdoutReader.awaitCompletion(deadline);
        final var stderrDone = stderrReader.awaitCompletion(deadline);
        if (!stdinDone || !stdoutDone || !stderrDone) {
            return new Outcome.Refused("interrupted or timed out while collecting javafmt's output", true);
        }
        final var stdout = new String(stdoutReader.result(), StandardCharsets.UTF_8);
        final var stderr = new String(stderrReader.result(), StandardCharsets.UTF_8);
        final var diagnostics = stderr.lines().flatMap(line -> ShimDiagnostic.parse(line).stream()).toList();
        if (exitValue != 0 && diagnostics.isEmpty()) {
            return new Outcome.Refused("javafmt exited with " + exitValue, true);
        }
        return new Outcome.Formatted(stdout, diagnostics);
    }

    /**
     * Whether a binary reporting {@code versionOutput} is new enough for this plugin to drive.
     *
     * <p>The floor is the plugin's own version rather than a hand-maintained constant. A constant
     * drifts: a gate written as "0.2.0 or newer" rejects the 0.1.x binary released alongside the
     * plugin, and blames the user's binary for it. Plugin and binary ship from the same version,
     * so requiring the binary to be at least as new as the plugin is both the honest requirement
     * and one that cannot fall out of step with a release.
     *
     * <p>Unparseable output is rejected rather than assumed good: a binary we cannot identify is
     * more likely to be something else on the PATH than a javafmt we should trust.
     */
    public static boolean isSupportedVersion(final String versionOutput) {
        Objects.requireNonNull(versionOutput, "versionOutput");
        return numericVersion(versionOutput)
                .map(reported -> compare(reported, MINIMUM_VERSION) >= 0)
                .orElse(false);
    }

    /** The lowest javafmt version this plugin will drive, as its {@code --version} line reports it. */
    public static String minimumVersion() {
        return MINIMUM_VERSION_TEXT;
    }

    /** The leading numeric components of a {@code javafmt --version} line, or empty if it is not one. */
    private static Optional<List<Integer>> numericVersion(final String versionOutput) {
        final var matcher = VERSION.matcher(versionOutput);
        return matcher.find() ? Optional.of(components(matcher.group(1))) : Optional.empty();
    }

    private static List<Integer> components(final String dotted) {
        return Stream.of(dotted.split("\\.")).map(Integer::parseInt).toList();
    }

    /** Component-wise comparison; a missing component counts as zero, so {@code 1.0} equals {@code 1.0.0}. */
    private static int compare(final List<Integer> left, final List<Integer> right) {
        return IntStream.range(0, Math.max(left.size(), right.size()))
                .map(i -> Integer.compare(componentAt(left, i), componentAt(right, i)))
                .filter(c -> c != 0)
                .findFirst()
                .orElse(0);
    }

    private static int componentAt(final List<Integer> components, final int index) {
        return index < components.size() ? components.get(index) : 0;
    }

    public Optional<String> version() {
        final Process process;
        try {
            process = new ProcessBuilder(executable.toString(), "--version").start();
        } catch (final IOException e) {
            return Optional.empty();
        }
        try {
            process.getOutputStream().close();
        } catch (final IOException e) {
            // Nothing to write, so a failure to close the child's stdin has no effect here.
        }
        final var deadline = Instant.now().plusSeconds(TIMEOUT_SECONDS);
        // Stderr is drained on its own thread purely so a chatty or malicious --version can never
        // fill its pipe and block the child, even though this method has no use for its content.
        final var stdoutReader = new StreamDrain(process.getInputStream(), "javafmt-version-stdout-reader");
        final var stderrReader = new StreamDrain(process.getErrorStream(), "javafmt-version-stderr-reader");
        final boolean finished;
        try {
            finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyAndCleanupInBackground(process, stdoutReader, stderrReader);
            return Optional.empty();
        }
        if (!finished) {
            destroyAndCleanupInBackground(process, stdoutReader, stderrReader);
            return Optional.empty();
        }
        // Same reasoning as collectAfterExit: an interrupted join must not be treated as "done",
        // or a truncated version string could be read as a real (if garbled) one.
        final var stdoutDone = stdoutReader.awaitCompletion(deadline);
        final var stderrDone = stderrReader.awaitCompletion(deadline);
        if (!stdoutDone || !stderrDone) {
            return Optional.empty();
        }
        final var out = new String(stdoutReader.result(), StandardCharsets.UTF_8);
        return process.exitValue() == 0 ? Optional.of(out.strip()) : Optional.empty();
    }

    /** Writes {@code source} to the child's stdin on a background thread and closes it, without ever throwing. */
    private static Thread writeStdinAsync(final Process process, final String source) {
        final var thread = new Thread(
                () -> {
                    try (var stdin = process.getOutputStream()) {
                        stdin.write(source.getBytes(StandardCharsets.UTF_8));
                    } catch (final IOException e) {
                        // The child exited or closed its stdin before consuming all of it (a broken
                        // pipe). waitFor and the exit code decide the outcome, not this write.
                    }
                },
                "javafmt-stdin-writer");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Kills {@code process} and best-effort-joins its helper threads on a detached daemon thread —
     * never on the caller's thread.
     *
     * <p>{@code Process.destroyForcibly} is not safe to call synchronously here. When the child
     * never drains stdin and {@code source} exceeds the OS pipe buffer, {@code writeStdinAsync}'s
     * thread parks inside a blocking, non-interruptible native {@code write()} while holding the
     * monitor on the stream {@code process.getOutputStream()} returned. The JDK's own destroy path
     * needs that identical monitor to close and flush that same stream, and nothing else is left to
     * send the kill signal that would unblock the writer — confirmed with {@code jstack} to hang
     * the calling thread indefinitely (not merely slowly) in exactly this shape. Running the kill
     * here instead means {@link #format} and {@link #version} return on their own schedule
     * regardless of whether this thread ever finishes; if the underlying JDK deadlock is real, this
     * thread leaks until the child eventually exits on its own (e.g. its own timeout, or the OS
     * reaping it at JVM shutdown), which is an acceptable trade against blocking every caller.
     */
    private static void destroyAndCleanupInBackground(
            final Process process,
            final Thread stdinWriter,
            final StreamDrain stdoutReader,
            final StreamDrain stderrReader) {
        final var cleanup = new Thread(
                () -> {
                    process.destroyForcibly();
                    final var grace = Instant.now().plusSeconds(CLEANUP_GRACE_SECONDS);
                    awaitCompletion(stdinWriter, grace);
                    stdoutReader.awaitCompletion(grace);
                    stderrReader.awaitCompletion(grace);
                },
                "javafmt-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    /** Same as the four-argument overload, for {@link #version}, which has no stdin-writer thread of its own. */
    private static void destroyAndCleanupInBackground(final Process process, final StreamDrain stdoutReader, final StreamDrain stderrReader) {
        final var cleanup = new Thread(
                () -> {
                    process.destroyForcibly();
                    final var grace = Instant.now().plusSeconds(CLEANUP_GRACE_SECONDS);
                    stdoutReader.awaitCompletion(grace);
                    stderrReader.awaitCompletion(grace);
                },
                "javafmt-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    /**
     * Waits for {@code thread} to finish, bounded by {@code deadline}, and reports whether it
     * actually did. Never throws: an interrupt while waiting re-arms the interrupt flag for the
     * caller and is reported as "not done" rather than assumed complete — {@code Thread.join} only
     * throws {@code InterruptedException} when it has to actually block on a still-live thread, so
     * this correctly distinguishes "finished before we even had to wait" from "was cut off
     * mid-wait", which a plain catch-and-continue cannot.
     */
    private static boolean awaitCompletion(final Thread thread, final Instant deadline) {
        final var remaining = Duration.between(Instant.now(), deadline).toMillis();
        if (remaining > 0) {
            try {
                thread.join(remaining);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return !thread.isAlive();
    }

    /** What one format attempt produced. */
    public sealed interface Outcome {

        /**
         * stdout carries the text to install. The CLI echoes the input unchanged when it cannot
         * format, so this is always safe to put in the document.
         */
        record Formatted(String output, List<ShimDiagnostic> diagnostics) implements Outcome {

            public Formatted {
                Objects.requireNonNull(output, "output");
                diagnostics = List.copyOf(diagnostics);
            }

            public boolean hasInternalError() {
                return diagnostics.stream().anyMatch(ShimDiagnostic::isInternal);
            }

            /** Whether any diagnostic is a failure rather than advisory; see {@link ShimDiagnostic#isAdvisory()}. */
            public boolean hasFailure() {
                return diagnostics.stream().anyMatch(diagnostic -> !diagnostic.isAdvisory());
            }
        }

        /** The binary could not be run at all. {@code internal} distinguishes a javafmt bug. */
        record Refused(String reason, boolean internal) implements Outcome {

            public Refused {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    /**
     * Reads one stream to completion on its own thread so a large or interleaved write on one
     * stream can never block behind an undrained sibling.
     *
     * <p>Package-private (rather than {@code private}) solely so a test can construct one directly
     * over a stream that deliberately never reaches EOF, to deterministically exercise the "join
     * didn't actually finish" path in {@link #collectAfterExit} without racing a real process.
     */
    static final class StreamDrain {

        private final Thread thread;

        private volatile byte[] result = new byte[0];

        StreamDrain(final InputStream in, final String name) {
            thread = new Thread(
                    () -> {
                        try {
                            result = in.readAllBytes();
                        } catch (final IOException e) {
                            // A forcibly destroyed process can sever the pipe mid-read; whatever was
                            // read so far (the initial empty array, in the worst case) is final.
                        }
                    },
                    name);
            thread.setDaemon(true);
            thread.start();
        }

        /** Bounded, interrupt-safe wait for the drain thread to finish; see {@link #awaitCompletion(Thread, Instant)}. */
        boolean awaitCompletion(final Instant deadline) {
            return JavafmtBinary.awaitCompletion(thread, deadline);
        }

        /** Whatever has been read so far. Only trustworthy as "complete" once {@link #awaitCompletion} returns true. */
        byte[] result() {
            return result;
        }
    }
}
