package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReleaseAssetTest {

    private static final String HASH = "a".repeat(64);

    @ParameterizedTest
    @CsvSource({ "Mac OS X, aarch64, javafmt-1.2.3-darwin-arm64", "Linux, amd64, javafmt-1.2.3-linux-x64", "Linux, x86_64, javafmt-1.2.3-linux-x64", "Windows 11, amd64, javafmt-1.2.3-win32-x64.exe", })
    void theAssetIsNamedAfterTheVersionAndTheIdesPlatform(final String osName, final String osArch, final String name) {
        assertThat(ReleaseAsset.forPlatform("1.2.3", osName, osArch, Map.of(name, HASH))).isEqualTo(new ReleaseAsset.Published(name, HASH));
    }

    @Test
    void aPlatformWithoutAPublishedBinaryIsUnpublished() {
        assertThat(ReleaseAsset.forPlatform("1.2.3", "Linux", "aarch64", Map.of("javafmt-1.2.3-linux-x64", HASH)))
                .isInstanceOfSatisfying(ReleaseAsset.Unpublished.class, unpublished -> assertThat(unpublished.reason()).contains("linux-arm64"));
    }

    @Test
    void anUnknownOperatingSystemIsUnpublished() {
        assertThat(ReleaseAsset.forPlatform("1.2.3", "SunOS", "sparcv9", Map.of()))
                .isInstanceOfSatisfying(ReleaseAsset.Unpublished.class, unpublished -> assertThat(unpublished.reason()).contains("SunOS"));
    }

    @Test
    void aBuildWithoutChecksumsSaysSo() {
        assertThat(ReleaseAsset.forPlatform("1.2.3", "Linux", "amd64", Map.of()))
                .isInstanceOfSatisfying(ReleaseAsset.Unpublished.class, unpublished -> assertThat(unpublished.reason()).contains("checksums"));
    }

    @Test
    void checksumsAreReadFromSha256sumOutput() {
        final var other = "B".repeat(64);
        final var sums = HASH + "  javafmt-1.2.3-linux-x64\n" + other + " *javafmt-1.2.3-win32-x64.exe\n\n";
        assertThat(ReleaseAsset.parseChecksums(sums))
                .containsExactlyInAnyOrderEntriesOf(Map.of("javafmt-1.2.3-linux-x64", HASH, "javafmt-1.2.3-win32-x64.exe", other.toLowerCase()));
    }

    @Test
    void thePluginCarriesTheChecksumsTheBuildWasGiven() throws IOException {
        final var given = Optional.ofNullable(System.getProperty("javafmt.nativeChecksums")).filter(path -> !path.isBlank()).map(Path::of);
        final var expected = given.isPresent() ? Files.readString(given.get()) : "";
        assertThat(ReleaseAsset.bundledChecksums()).isEqualTo(ReleaseAsset.parseChecksums(expected));
    }
}
