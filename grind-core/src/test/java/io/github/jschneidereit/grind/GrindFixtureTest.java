package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GrindFixtureTest {

    static Stream<Arguments> fixtures() throws URISyntaxException, IOException {
        final var fixturesRoot = GrindFixtureTest.class.getClassLoader().getResource("test-fixtures");
        if (fixturesRoot == null) {
            return Stream.empty();
        }
        final var root = Paths.get(fixturesRoot.toURI());
        try (final var listing = Files.list(root)) {
            return listing
                .filter(Files::isDirectory)
                .filter(dir -> Files.exists(dir.resolve("input.java")))
                .<Arguments>map(dir -> {
                    final var expected = dir.resolve("expected.java");
                    if (!Files.exists(expected)) {
                        throw new IllegalStateException(
                            "Fixture '" + dir.getFileName() + "' has input.java but no expected.java");
                    }
                    return Arguments.of(
                        dir.getFileName().toString(),
                        read(dir.resolve("input.java")),
                        read(expected));
                })
                .sorted((a, b) -> ((String) a.get()[0]).compareTo((String) b.get()[0]))
                .toList()
                .stream();
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path).stripTrailing();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void fixture(final String name, final String input, final String expected) {
        final var formatted = Grind.format(input);
        assertThat(formatted).isEqualTo(expected);
        assertThat(Grind.format(formatted)).isEqualTo(formatted);
    }
}
