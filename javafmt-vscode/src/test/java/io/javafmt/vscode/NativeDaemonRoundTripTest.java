package io.javafmt.vscode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Runs the compiled native daemon binary (if present) through a real NDJSON round trip. Jackson
 * deserializes {@link FormatRequest} and serializes {@link FormatResponse}/{@link DiagnosticDto}
 * reflectively, so a native image without the committed reachability metadata under
 * {@code META-INF/native-image/io.javafmt/javafmt-vscode} crashes on its first request with
 * {@code UnsupportedFeatureError}. This test is the guardrail: it fails whenever these DTOs
 * change in a way the committed metadata no longer covers, instead of only discovering the break
 * once a real VSIX ships.
 *
 * <p>The native binary only exists after {@code gradle :javafmt-vscode:nativeCompile}, which most
 * developer machines never run, so this test is skipped (not failed) when the binary is absent.
 * {@code native.yml} is the only place that runs {@code nativeCompile} and it invokes this test
 * explicitly right after, so the assertion is never silently skipped in CI.
 */
class NativeDaemonRoundTripTest {

    private static final Path BINARY_DIR = Path.of("build", "native", "nativeCompile");

    @Test
    void survivesRealRequests() throws IOException, InterruptedException {
        final var binary = locateBinary();
        assumeTrue(binary.isPresent(), "native binary not built; run gradle :javafmt-vscode:nativeCompile first");
        final var process = new ProcessBuilder(binary.orElseThrow().toString()).start();
        try (final var stdin = process.outputWriter(StandardCharsets.UTF_8)) {
            stdin.write("""
                        {"id":"1","source":"class A{}"}
                        {"id":"2","source":"class Foo { void m(int x) { switch (x) { case 1: System.out.println(); case 2: break; } } }"}
                        """);
        }
        assertThat(process.waitFor(30, TimeUnit.SECONDS))
                .as("daemon exited before timeout")
                .isTrue();
        final var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue())
                .as("daemon exit code; stderr: %s", stderr)
                .isZero();
        final var lines = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        assertThat(lines).hasSize(2);
        // Empty diagnostics list: the reflection-heavy path Jackson takes when there is nothing
        // to serialize inside the list is different from a populated one, so both are covered.
        final var clean = Json.parse(lines.get(0), FormatResponse.class);
        assertThat(clean.id()).isEqualTo("1");
        assertThat(clean.output()).isEqualTo("class A {}\n");
        assertThat(clean.diagnostics()).isEmpty();
        // A populated diagnostics list exercises DiagnosticDto's own reflective accessors.
        final var withDiagnostics = Json.parse(lines.get(1), FormatResponse.class);
        assertThat(withDiagnostics.id()).isEqualTo("2");
        assertThat(withDiagnostics.diagnostics())
                .isNotEmpty()
                .allSatisfy(d -> assertThat(d.severity()).isEqualTo("warning"));
    }

    private static Optional<Path> locateBinary() {
        return Stream.of(BINARY_DIR.resolve("javafmt-daemon"), BINARY_DIR.resolve("javafmt-daemon.exe"))
                .filter(Files::isExecutable)
                .findFirst();
    }
}
