package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.testFramework.TestApplicationManager;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class JavafmtBinaryProviderTest {

    @BeforeAll
    static void startTheApplication() {
        TestApplicationManager.getInstance();
    }

    private static final String NAME = "javafmt-" + FakeRelease.VERSION + "-linux-x64";

    private static final byte[] BINARY = "#!/bin/sh\necho 'javafmt 1.2.3'\n".getBytes(StandardCharsets.UTF_8);

    private static final ReleaseAsset ASSET = new ReleaseAsset.Published(NAME, FakeRelease.sha256(BINARY));

    @Test
    void concurrentFirstFormatsShareOneDownload(@TempDir final Path dir) throws Exception {
        final var go = new CountDownLatch(1);
        try (final var release = FakeRelease.servingAfter(go, Map.of(NAME, BINARY))) {
            final var provider = provider(release.base(), dir.resolve("cache"), ASSET, dir.resolve("empty-path").toString());
            final var first = provider.download(null);
            final var second = provider.download(null);
            go.countDown();
            assertThat(second).isSameAs(first);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(new Installation.Installed(dir.resolve("cache").resolve(NAME)));
            assertThat(release.requests()).isEqualTo(1);
        }
    }

    @Test
    void aFinishedFailureIsRetriedOnTheNextFormat(@TempDir final Path dir) throws Exception {
        try (final var release = FakeRelease.serving(Map.of())) {
            final var provider = provider(release.base(), dir.resolve("cache"), ASSET, dir.resolve("empty-path").toString());
            assertThat(provider.download(null).get(20, TimeUnit.SECONDS)).isInstanceOf(Installation.Failed.class);
            assertThat(provider.download(null).get(20, TimeUnit.SECONDS)).isInstanceOf(Installation.Failed.class);
            assertThat(release.requests()).isEqualTo(2);
        }
    }

    @Test
    void anInstalledBinaryIsUsedWithoutTheNetwork(@TempDir final Path dir) throws Exception {
        final var cache = Files.createDirectories(dir.resolve("cache"));
        final var installed = Files.write(cache.resolve(NAME), BINARY);
        assertThat(installed.toFile().setExecutable(true)).isTrue();
        try (final var release = FakeRelease.serving(Map.of(NAME, BINARY))) {
            final var provider = provider(release.base(), cache, ASSET, dir.resolve("empty-path").toString());
            assertThat(provider.download(null)).isCompletedWithValue(new Installation.Installed(installed));
            assertThat(provider.describe()).contains(installed.toString());
            assertThat(release.requests()).isZero();
        }
    }

    @Test
    void eachDownloadNoticeIsShownOncePerSession(@TempDir final Path dir) throws Exception {
        final var provider = provider(FakeRelease.unreachable(), dir, ASSET, "");
        assertThat(provider.claimDownloadNotice()).isTrue();
        assertThat(provider.claimDownloadNotice()).isFalse();
        assertThat(provider.claimFailureNotice()).isTrue();
        assertThat(provider.claimFailureNotice()).isFalse();
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void aSupportedJavafmtOnPathIsPreferredToADownload(@TempDir final Path dir) throws Exception {
        final var onPath = javafmtReporting(dir, "javafmt 99.0.0");
        final var provider = provider(FakeRelease.unreachable(), dir.resolve("cache"), ASSET, dir.toString());
        assertThat(provider.onPath().map(BinaryIdentity::path)).contains(onPath);
        assertThat(provider.describe()).contains(onPath.toString()).contains("PATH");
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void aTooOldJavafmtOnPathIsPassedOver(@TempDir final Path dir) throws Exception {
        javafmtReporting(dir, "javafmt 0.0.1");
        final var provider = provider(FakeRelease.unreachable(), dir.resolve("cache"), ASSET, dir.toString());
        assertThat(provider.onPath()).isEmpty();
    }

    @Test
    void theSettingsPageSaysABinaryWillBeDownloaded(@TempDir final Path dir) throws Exception {
        final var provider = provider(FakeRelease.unreachable(), dir, ASSET, "");
        assertThat(provider.describe()).contains(NAME).contains("downloaded");
    }

    @Test
    void theSettingsPageSaysWhyNothingCanBeDownloaded(@TempDir final Path dir) throws Exception {
        final var provider = provider(FakeRelease.unreachable(), dir, new ReleaseAsset.Unpublished("no binary for SunOS"), "");
        assertThat(provider.describe()).contains("no binary for SunOS");
    }

    private static JavafmtBinaryProvider provider(final URI base, final Path cache, final ReleaseAsset asset, final String searchPath) {
        return new JavafmtBinaryProvider(new BinaryInstaller(base, cache, asset), JavafmtBinaryProviderTest::runOnNewThread, () -> searchPath);
    }

    static void runOnNewThread(final Task.Backgroundable task) {
        Thread.ofPlatform()
                .start(() -> {
                    try {
                        task.run(new EmptyProgressIndicator());
                    } finally {
                        task.onFinished();
                    }
                });
    }

    private static Path javafmtReporting(final Path dir, final String version) throws IOException {
        final var path = dir.resolve("javafmt");
        Files.writeString(path, "#!/bin/sh\necho '" + version + "'\n");
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
    }
}
