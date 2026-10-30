package io.javafmt.intellij;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.io.HttpRequests;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class BinaryInstaller {

    private static final URI RELEASES = URI.create("https://github.com/javafmt-io/javafmt/releases/download/");

    private final URI releaseBase;

    private final Path installDir;

    private final ReleaseAsset asset;

    BinaryInstaller(final URI releaseBase, final Path installDir, final ReleaseAsset asset) {
        this.releaseBase = Objects.requireNonNull(releaseBase, "releaseBase");
        this.installDir = Objects.requireNonNull(installDir, "installDir");
        this.asset = Objects.requireNonNull(asset, "asset");
    }

    static BinaryInstaller forThisPlugin() {
        return new BinaryInstaller(
                RELEASES.resolve("v" + JavafmtPluginVersion.VERSION + "/"),
                PathManager.getSystemDir().resolve("javafmt"),
                ReleaseAsset.forThisIde());
    }

    ReleaseAsset asset() {
        return asset;
    }

    Optional<Path> installed() {
        return switch (asset) {
            case ReleaseAsset.Published published ->
                Optional.of(installDir.resolve(published.name()))
                        .filter(path -> Files.isRegularFile(path) && Files.isExecutable(path));
            case ReleaseAsset.Unpublished ignored -> Optional.empty();
        };
    }

    Installation install(final ProgressIndicator indicator) {
        return switch (asset) {
            case ReleaseAsset.Unpublished unpublished -> new Installation.Failed(unpublished.reason());
            case ReleaseAsset.Published published ->
                installed()
                        .<Installation>map(Installation.Installed::new)
                        .orElseGet(() -> download(published, indicator));
        };
    }

    private Installation download(final ReleaseAsset.Published published, final ProgressIndicator indicator) {
        final var url = releaseBase.resolve(published.name()).toString();
        try {
            Files.createDirectories(installDir);
            final var staging = Files.createTempDirectory(installDir, published.name() + ".download");
            try {
                return verifyAndInstall(url, published, fetch(url, published.name(), staging, indicator));
            } finally {
                NioFiles.deleteRecursively(staging);
            }
        } catch (final ProcessCanceledException e) {
            return new Installation.Cancelled();
        } catch (final HttpRequests.HttpStatusException e) {
            return new Installation.Failed("downloading " + url + " failed with HTTP status " + e.getStatusCode());
        } catch (final IOException e) {
            return installed()
                    .<Installation>map(Installation.Installed::new)
                    .orElseGet(() -> new Installation.Failed("could not download " + url + ": " + e));
        }
    }

    private static Path fetch(final String url, final String name, final Path staging, final ProgressIndicator indicator) throws IOException {
        final var service = DownloadableFileService.getInstance();
        final var downloader = service.createDownloader(List.of(service.createFileDescription(url, name)), name);
        try {
            return ProgressManager.getInstance()
                    .runProcess(
                            () -> {
                                try {
                                    return downloader.download(staging.toFile()).getFirst().first.toPath();
                                } catch (final IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            },
                            indicator);
        } catch (final UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private Installation verifyAndInstall(final String url, final ReleaseAsset.Published published, final Path partial) throws IOException {
        if (!sha256(partial).equalsIgnoreCase(published.sha256())) {
            return new Installation.Failed(url + " does not match the SHA-256 checksum this plugin was built with");
        }
        if (!partial.toFile().setExecutable(true)) {
            return new Installation.Failed("could not mark " + partial + " executable");
        }
        final var target = installDir.resolve(published.name());
        Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        return new Installation.Installed(target);
    }

    private static String sha256(final Path file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (final var in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
