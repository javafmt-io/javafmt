package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.testFramework.TestApplicationManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class BinaryInstallerTest {

    @BeforeAll
    static void startTheApplication() {
        TestApplicationManager.getInstance();
    }

    private static final String NAME = "javafmt-" + FakeRelease.VERSION + "-linux-x64";

    private static final byte[] BINARY = "#!/bin/sh\necho 'javafmt 1.2.3'\n".getBytes(StandardCharsets.UTF_8);

    private static final ReleaseAsset ASSET = new ReleaseAsset.Published(NAME, FakeRelease.sha256(BINARY));

    @Test
    void aVerifiedDownloadIsInstalledAsAnExecutable(@TempDir final Path dir) throws Exception {
        try (final var release = FakeRelease.serving(Map.of(NAME, BINARY))) {
            final var installation = new BinaryInstaller(release.base(), dir, ASSET).install(new EmptyProgressIndicator());
            assertThat(installation).isEqualTo(new Installation.Installed(dir.resolve(NAME)));
            assertThat(dir.resolve(NAME)).isExecutable().hasBinaryContent(BINARY);
            assertThat(files(dir)).containsExactly(dir.resolve(NAME));
        }
    }

    @Test
    void aDownloadThatDoesNotMatchItsChecksumIsDiscarded(@TempDir final Path dir) throws Exception {
        try (final var release = FakeRelease.serving(Map.of(NAME, "tampered".getBytes(StandardCharsets.UTF_8)))) {
            final var installation = new BinaryInstaller(release.base(), dir, ASSET).install(new EmptyProgressIndicator());
            assertThat(installation).isInstanceOfSatisfying(Installation.Failed.class, failed -> assertThat(failed.reason()).contains("SHA-256"));
            assertThat(files(dir)).isEmpty();
        }
    }

    @Test
    void aMissingReleaseAssetFails(@TempDir final Path dir) throws Exception {
        try (final var release = FakeRelease.serving(Map.of())) {
            final var installation = new BinaryInstaller(release.base(), dir, ASSET).install(new EmptyProgressIndicator());
            assertThat(installation).isInstanceOfSatisfying(Installation.Failed.class, failed -> assertThat(failed.reason()).contains("404"));
            assertThat(files(dir)).isEmpty();
        }
    }

    @Test
    void anUnreachableServerFails(@TempDir final Path dir) throws Exception {
        final var installation = new BinaryInstaller(FakeRelease.unreachable(), dir, ASSET).install(new EmptyProgressIndicator());
        assertThat(installation).isInstanceOfSatisfying(Installation.Failed.class, failed -> assertThat(failed.reason()).contains(NAME));
        assertThat(files(dir)).isEmpty();
    }

    @Test
    void anUnpublishedAssetFailsWithoutTouchingTheNetwork(@TempDir final Path dir) throws Exception {
        try (final var release = FakeRelease.serving(Map.of(NAME, BINARY))) {
            final var installation = new BinaryInstaller(
                    release.base(),
                    dir,
                    new ReleaseAsset.Unpublished("no binary for this platform")).install(new EmptyProgressIndicator());
            assertThat(installation).isEqualTo(new Installation.Failed("no binary for this platform"));
            assertThat(release.requests()).isZero();
        }
    }

    @Test
    void cancellingMidDownloadLeavesNothingBehind(@TempDir final Path dir) throws Exception {
        final var halfway = new CountDownLatch(1);
        final var large = new byte[4_000];
        try (final var release = FakeRelease.tricklingAfterHalf(halfway, Map.of(NAME, large))) {
            final var indicator = new EmptyProgressIndicator();
            final var installer = new BinaryInstaller(release.base(), dir, new ReleaseAsset.Published(NAME, FakeRelease.sha256(large)));
            final var installation = CompletableFuture.supplyAsync(() -> installer.install(indicator));
            assertThat(halfway.await(20, TimeUnit.SECONDS)).isTrue();
            indicator.cancel();
            assertThat(installation.get(20, TimeUnit.SECONDS)).isEqualTo(new Installation.Cancelled());
            assertThat(files(dir)).isEmpty();
        }
    }

    @Test
    void anInstalledBinaryIsReusedWithoutTheNetwork(@TempDir final Path dir) throws Exception {
        final var installed = Files.write(dir.resolve(NAME), BINARY);
        assertThat(installed.toFile().setExecutable(true)).isTrue();
        try (final var release = FakeRelease.serving(Map.of(NAME, BINARY))) {
            final var installer = new BinaryInstaller(release.base(), dir, ASSET);
            assertThat(installer.installed()).contains(installed);
            assertThat(installer.install(new EmptyProgressIndicator())).isEqualTo(new Installation.Installed(installed));
            assertThat(release.requests()).isZero();
        }
    }

    private static List<Path> files(final Path dir) throws IOException {
        try (final var listing = Files.list(dir)) {
            return listing.toList();
        }
    }
}
