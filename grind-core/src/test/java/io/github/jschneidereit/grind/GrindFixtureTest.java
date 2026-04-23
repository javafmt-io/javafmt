package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
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
                .map(dir -> {
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
                .sorted(Comparator.comparing(a -> ((String) a.get()[0])))
                .toList()
                .stream();
        }
    }

    static Stream<Arguments> idempotentFixtures() throws URISyntaxException, IOException {
        final var root = GrindFixtureTest.class.getClassLoader().getResource("test-fixtures/idempotent");
        if (root == null) {
            return Stream.empty();
        }
        final var dir = Paths.get(root.toURI());
        try (final var listing = Files.list(dir)) {
            return listing
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .<Arguments>map(path -> {
                    final var name = path.getFileName().toString();
                    return Arguments.of(name.substring(0, name.length() - ".java".length()), read(path));
                })
                .sorted(Comparator.comparing(a -> ((String) a.get()[0])))
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("idempotentFixtures")
    void idempotent(final String name, final String source) {
        assertThat(Grind.format(source)).isEqualTo(source);
    }
}
