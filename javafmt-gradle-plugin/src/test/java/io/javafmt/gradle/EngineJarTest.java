package io.javafmt.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class EngineJarTest {

    @Test
    void extractsTheEngineJarNamedByItsHash(@TempDir final Path dir) throws IOException {
        final var extracted = EngineJar.extractTo(dir.resolve("cache").toFile(), "abc");
        assertThat(extracted.toPath()).isEqualTo(dir.resolve("cache").resolve("javafmt-engine-abc.jar"));
        assertThat(extracted).isNotEmpty();
        assertThat(Files.readAllBytes(extracted.toPath())).startsWith('P', 'K');
    }

    @Test
    void reusesAnExistingJarInsteadOfExtractingAgain(@TempDir final Path dir) throws IOException {
        final var first = EngineJar.extractTo(dir.toFile(), "abc");
        Files.writeString(first.toPath(), "sentinel");
        final var second = EngineJar.extractTo(dir.toFile(), "abc");
        assertThat(second).isEqualTo(first);
        assertThat(Files.readString(second.toPath())).isEqualTo("sentinel");
    }

    @Test
    void extractionsRacingOnTheSameTargetBothSucceed(@TempDir final Path dir) throws IOException {
        final var barrier = new CyclicBarrier(2);
        final var extractions = IntStream.range(0, 2)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        barrier.await();
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return EngineJar.extractTo(dir.toFile(), "abc");
                }))
                .toList();
        final var jars = extractions.stream()
                .map(CompletableFuture::join)
                .toList();
        assertThat(jars).containsOnly(dir.resolve("javafmt-engine-abc.jar").toFile());
        try (final var files = Files.list(dir)) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("javafmt-engine-abc.jar");
        }
    }
}
