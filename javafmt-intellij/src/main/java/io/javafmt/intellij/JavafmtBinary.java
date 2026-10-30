package io.javafmt.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessIOExecutorService;
import io.javafmt.daemon.protocol.Diagnostic;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Finds the native {@code javafmt} binary and checks its version. Formatting itself goes through
 * the long-lived {@code javafmt --daemon} process that {@link JavafmtDaemons} keeps per project.
 *
 * <p>Out of process is what lets this module compile to Java 21 bytecode while the formatter stays
 * on Java 25: the binary embeds its own runtime, so {@code javafmt-core} is never loaded by the
 * IDE's JBR.
 *
 * <p><b>{@link #version} returns within roughly {@code TIMEOUT} (10s) of being called, or at once
 * when the calling thread is interrupted.</b> The probe runs on the platform's process I/O pool and
 * killing a hung child happens there too, never on the calling thread.
 */
public final class JavafmtBinary {

    /** Real work is ~10 ms; this only ever catches a hang. */
    static final Duration TIMEOUT = Duration.ofSeconds(10);

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

    public VersionProbe version() {
        final CapturingProcessHandler handler;
        try {
            handler = new CapturingProcessHandler(new GeneralCommandLine(executable.toString(), "--version"));
        } catch (final ExecutionException e) {
            return new VersionProbe.Failed();
        }
        final var run = CompletableFuture.supplyAsync(
                () -> handler.runProcess(Math.toIntExact(TIMEOUT.toMillis()), true),
                ProcessIOExecutorService.INSTANCE);
        try {
            final var output = run.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (output.isTimeout()) {
                return new VersionProbe.Inconclusive();
            }
            return output.getExitCode() == 0 ? new VersionProbe.Reported(output.getStdout().strip()) : new VersionProbe.Failed();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            ProcessIOExecutorService.INSTANCE.execute(handler::destroyProcess);
            return new VersionProbe.Inconclusive();
        } catch (final TimeoutException e) {
            return new VersionProbe.Inconclusive();
        } catch (final java.util.concurrent.ExecutionException e) {
            return new VersionProbe.Failed();
        }
    }

    public sealed interface VersionProbe {

        default Optional<String> reportedVersion() {
            return this instanceof Reported reported ? Optional.of(reported.output()) : Optional.empty();
        }

        record Reported(String output) implements VersionProbe {

            public Reported {
                Objects.requireNonNull(output, "output");
            }
        }

        record Failed() implements VersionProbe {}

        record Inconclusive() implements VersionProbe {}
    }

    /** What one format attempt produced. */
    public sealed interface Outcome {

        /**
         * {@code output} is the text to install. javafmt returns the input unchanged when it cannot
         * format, so this is always safe to put in the document.
         */
        record Formatted(String output, List<Diagnostic> diagnostics) implements Outcome {

            public Formatted {
                Objects.requireNonNull(output, "output");
                diagnostics = List.copyOf(diagnostics);
            }

            public boolean hasInternalError() {
                return diagnostics.stream().anyMatch(diagnostic -> diagnostic.getKind() == Kind.KIND_INTERNAL);
            }

            public boolean hasFailure() {
                return diagnostics.stream().anyMatch(diagnostic -> diagnostic.getSeverity() != Severity.SEVERITY_WARNING);
            }
        }

        record Cancelled() implements Outcome {}

        /** The binary could not be run at all. {@code internal} distinguishes a javafmt bug. */
        record Refused(String reason, boolean internal) implements Outcome {

            public Refused {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
