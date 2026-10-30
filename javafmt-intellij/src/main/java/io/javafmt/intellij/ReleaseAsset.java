package io.javafmt.intellij;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

sealed interface ReleaseAsset {

    String CHECKSUMS_RESOURCE = "/io/javafmt/intellij/native-checksums.txt";

    static ReleaseAsset forThisIde() {
        return forPlatform(JavafmtPluginVersion.VERSION, System.getProperty("os.name"), System.getProperty("os.arch"), bundledChecksums());
    }

    static ReleaseAsset forPlatform(final String version, final String osName, final String osArch, final Map<String, String> checksums) {
        final var os = operatingSystem(osName.toLowerCase(Locale.ROOT));
        if (os.isEmpty()) {
            return new Unpublished("javafmt publishes no native binary for " + osName);
        }
        final var target = os.get() + "-" + architecture(osArch.toLowerCase(Locale.ROOT));
        final var name = "javafmt-" + version + "-" + target + ("win32".equals(os.get()) ? ".exe" : "");
        if (checksums.isEmpty()) {
            return new Unpublished("this build of the javafmt plugin carries no binary checksums to verify a download against");
        }
        return Optional.ofNullable(checksums.get(name))
                .<ReleaseAsset>map(sha256 -> new Published(name, sha256))
                .orElseGet(() -> new Unpublished("javafmt " + version + " publishes no native binary for " + target));
    }

    static Map<String, String> parseChecksums(final String sha256sums) {
        return sha256sums.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(line -> line.split("\\s+\\*?", 2))
                .collect(Collectors.toUnmodifiableMap(fields -> fields[1], fields -> fields[0].toLowerCase(Locale.ROOT)));
    }

    static Map<String, String> bundledChecksums() {
        try (final var in = Objects.requireNonNull(ReleaseAsset.class.getResourceAsStream(CHECKSUMS_RESOURCE), CHECKSUMS_RESOURCE)) {
            return parseChecksums(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<String> operatingSystem(final String osName) {
        if (osName.startsWith("mac")) {
            return Optional.of("darwin");
        }
        if (osName.startsWith("linux")) {
            return Optional.of("linux");
        }
        return osName.startsWith("windows") ? Optional.of("win32") : Optional.empty();
    }

    private static String architecture(final String osArch) {
        return switch (osArch) {
            case "aarch64", "arm64" -> "arm64";
            case "amd64", "x86_64" -> "x64";
            default -> osArch;
        };
    }

    record Published(String name, String sha256) implements ReleaseAsset {

        public Published {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(sha256, "sha256");
        }
    }

    record Unpublished(String reason) implements ReleaseAsset {

        public Unpublished {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
