package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.Tree;

import io.github.jschneidereit.grind.GrindConfig;
import io.github.jschneidereit.grind.parser.JavaParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RendererCoverageTest {

    private static final Set<Tree.Kind> EXPECTED_FALLBACK_KINDS = Set.of(
        Tree.Kind.IDENTIFIER, Tree.Kind.PRIMITIVE_TYPE,
        Tree.Kind.BOOLEAN_LITERAL, Tree.Kind.CHAR_LITERAL,
        Tree.Kind.DOUBLE_LITERAL, Tree.Kind.FLOAT_LITERAL,
        Tree.Kind.INT_LITERAL, Tree.Kind.LONG_LITERAL,
        Tree.Kind.NULL_LITERAL, Tree.Kind.STRING_LITERAL,
        Tree.Kind.MODIFIERS, Tree.Kind.EMPTY_STATEMENT,
        Tree.Kind.BREAK, Tree.Kind.CONTINUE,
        // Known renderer gaps: children are expressions that should recurse,
        // not be toString'd. Remove from this set when a dedicated renderer lands.
        Tree.Kind.TYPE_PARAMETER,
        Tree.Kind.CONSTANT_CASE_LABEL,
        Tree.Kind.UNION_TYPE);

    private static final Set<String> EXPECTED_FALLBACK_FIXTURES = Set.of(
        "switch-colon-form-preserved",
        "switch-mixed-form");

    static Stream<Arguments> coverageFixtures() throws URISyntaxException, IOException {
        final var fixturesRoot = RendererCoverageTest.class.getClassLoader().getResource("test-fixtures");
        if (fixturesRoot == null) {
            return Stream.empty();
        }
        final var root = Paths.get(fixturesRoot.toURI());
        try (final var listing = Files.list(root)) {
            return listing
                .filter(Files::isDirectory)
                .filter(dir -> !EXPECTED_FALLBACK_FIXTURES.contains(dir.getFileName().toString()))
                .flatMap(RendererCoverageTest::inputs)
                .sorted(Comparator.comparing(a -> (String) a.get()[0]))
                .toList()
                .stream();
        }
    }

    private static Stream<Arguments> inputs(final Path dir) {
        try (final var listing = Files.list(dir)) {
            return listing
                .filter(path -> {
                    final var name = path.getFileName().toString();
                    return name.equals("input.java")
                        || (name.startsWith("input-") && name.endsWith(".java"))
                        || (dir.getFileName().toString().equals("idempotent") && name.endsWith(".java"));
                })
                .sorted()
                .<Arguments>map(path -> Arguments.of(dir.getFileName() + "/" + path.getFileName(), read(path)))
                .toList()
                .stream();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("coverageFixtures")
    void onlyExpectedKindsFallBackToToString(final String name, final String source) {
        final var unit = JavaParser.parseUnit(source);
        final var result = DocBuilder.buildWithFallbacks(unit, GrindConfig.defaults());
        final var unexpected = result.fallbacks().stream()
            .filter(t -> !EXPECTED_FALLBACK_KINDS.contains(t.getKind()))
            .map(Tree::getKind)
            .toList();
        assertThat(unexpected)
            .as("fixture '%s' produced unexpected textFallback invocations", name)
            .isEmpty();
    }
}
