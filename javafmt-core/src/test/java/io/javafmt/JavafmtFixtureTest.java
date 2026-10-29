package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.javafmt.parser.JavaParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JavafmtFixtureTest {

    static Stream<Arguments> fixtures() throws URISyntaxException, IOException {
        final var fixturesRoot = JavafmtFixtureTest.class.getClassLoader().getResource("test-fixtures");
        if (fixturesRoot == null) {
            return Stream.empty();
        }
        final var root = Paths.get(fixturesRoot.toURI());
        try (final var listing = Files.list(root)) {
            return listing.filter(Files::isDirectory)
                    .filter(dir -> !dir.getFileName().toString().equals("idempotent"))
                    .flatMap(JavafmtFixtureTest::variants)
                    .sorted(Comparator.comparing(a -> ((String) a.get()[0])))
                    .toList()
                    .stream();
        }
    }

    private static Stream<Arguments> variants(final Path dir) {
        final var expected = dir.resolve("expected.java");
        if (!Files.exists(expected)) {
            throw new IllegalStateException("Fixture '" + dir.getFileName() + "' has no expected.java");
        }
        final var expectedContent = read(expected);
        final var inputs = inputFiles(dir);
        if (inputs.isEmpty()) {
            throw new IllegalStateException("Fixture '" + dir.getFileName() + "' has no input files");
        }
        return inputs.stream().map(input -> Arguments.of(displayName(dir, input), read(input), expectedContent));
    }

    private static List<Path> inputFiles(final Path dir) {
        try (final var listing = Files.list(dir)) {
            return listing.filter(path -> {
                final var name = path.getFileName().toString();
                return name.equals("input.java") || (name.startsWith("input-") && name.endsWith(".java"));
            })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String displayName(final Path dir, final Path input) {
        final var fixture = dir.getFileName().toString();
        final var name = input.getFileName().toString();
        if (name.equals("input.java")) {
            return fixture;
        }
        final var variant = name.substring("input-".length(), name.length() - ".java".length());
        return fixture + "-" + variant;
    }

    static Stream<Arguments> idempotentFixtures() throws URISyntaxException, IOException {
        final var root = JavafmtFixtureTest.class.getClassLoader().getResource("test-fixtures/idempotent");
        if (root == null) {
            return Stream.empty();
        }
        final var dir = Paths.get(root.toURI());
        try (final var listing = Files.list(dir)) {
            return listing.filter(path -> path.getFileName().toString().endsWith(".java"))
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
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void fixture(final String name, final String input, final String expected) {
        final var formatted = formatVerified(name, input);
        assertThat(formatted.stripTrailing()).isEqualTo(expected);
        assertThat(formatVerified(name, formatted)).isEqualTo(formatted);
        assertWellFormed(name, formatted);
        assertStillCompiles(name, input, formatted);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("idempotentFixtures")
    void idempotent(final String name, final String source) {
        final var formatted = formatVerified(name, source);
        assertThat(formatted.stripTrailing()).isEqualTo(source);
        assertWellFormed(name, formatted);
        assertStillCompiles(name, source, formatted);
    }

    /**
     * Formats with output verification on, so a fixture whose rendering silently drops a
     * token or a comment fails here — with the dropped text named — instead of failing as an
     * unexplained diff against {@code expected.java}.
     */
    private static String formatVerified(final String name, final String source) {
        final var result = Javafmt.formatWithResult(source, JavafmtConfig.defaults().withVerifyOutput(true));
        final var internal = result.diagnostics()
                .stream()
                .filter(d -> d instanceof Diagnostic.InternalError)
                .map(Diagnostic::message)
                .toList();
        assertThat(internal)
                .as("fixture '%s' must format without an internal error", name)
                .isEmpty();
        return result.output();
    }

    private static void assertWellFormed(final String name, final String formatted) {
        assertThatCode(() -> JavaParser.parseUnit(formatted))
                .as("formatted output for fixture '%s' must re-parse without errors", name)
                .doesNotThrowAnyException();
    }

    /**
     * Differential: javafmt is only at fault when it turns something that compiled into
     * something that doesn't. Several fixtures deliberately exercise the renderer on inputs
     * javac rejects at attribution (mixed arrow/colon switch arms, an {@code int[]::new}
     * bound to a {@code Supplier}), and those are not javafmt's problem.
     */
    private static void assertStillCompiles(final String name, final String input, final String formatted) {
        if (!(CompileCheck.check(input) instanceof CompileCheck.Outcome.Compiles)) {
            return;
        }
        assertThat(CompileCheck.check(formatted))
                .as("fixture '%s' compiled before formatting but not after; output:%n%s", name, formatted)
                .isNotInstanceOf(CompileCheck.Outcome.Broken.class);
    }
}
