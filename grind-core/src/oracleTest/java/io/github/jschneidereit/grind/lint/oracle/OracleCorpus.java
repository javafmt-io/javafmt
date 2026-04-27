package io.github.jschneidereit.grind.lint.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.Grind;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

/**
 * Helpers for lint-rule oracle tests. Each rule has a corpus directory under
 * {@code src/oracleTest/resources/corpus/<rule>/}; tests load it with {@link #load} and
 * assert that grind's output is Checkstyle-clean for the corresponding rule.
 *
 * <p>Two assertions matter for an oracle suite:
 * <ul>
 *   <li><b>Soundness</b> ({@link #assertGrindMakesItClean}): grind's output must not contain
 *       any violations the oracle would flag. This proves grind doesn't miss cases.</li>
 *   <li><b>Coverage</b> ({@link #assertCorpusExercisesRule}): at least one corpus file must
 *       have produced violations originally. Without this, soundness is vacuously satisfied
 *       by a corpus of already-clean files.</li>
 * </ul>
 */
public final class OracleCorpus {

    public static Stream<Arguments> load(final String corpusSubdir) {
        final var dir = locateCorpus(corpusSubdir);
        try (final var listing = Files.list(dir)) {
            return listing
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .<Arguments>map(p -> Arguments.of(p.getFileName().toString(), readString(p)))
                .toList()
                .stream();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void assertGrindMakesItClean(
            final String name,
            final String source,
            final String checkClassName,
            final Map<String, String> checkProperties) {
        Objects.requireNonNull(name, "name");
        final var formatted = Grind.format(source);
        final var violations = CheckstyleOracle.run(formatted, checkClassName, checkProperties);
        assertThat(violations)
            .as("grind output for corpus file '%s' should have zero %s violations; output:%n%s",
                name, simpleName(checkClassName), formatted)
            .isEmpty();
    }

    public static void assertCorpusExercisesRule(
            final String corpusSubdir,
            final String checkClassName,
            final Map<String, String> checkProperties) {
        final var dirty = corpusFiles(corpusSubdir).stream()
            .filter(p -> !CheckstyleOracle.run(readString(p), checkClassName, checkProperties).isEmpty())
            .map(p -> p.getFileName().toString())
            .toList();
        assertThat(dirty)
            .as("oracle corpus '%s' must contain at least one file with %s violations "
                    + "(otherwise soundness assertions pass vacuously)",
                corpusSubdir, simpleName(checkClassName))
            .isNotEmpty();
    }

    private static List<Path> corpusFiles(final String corpusSubdir) {
        final var dir = locateCorpus(corpusSubdir);
        try (final var listing = Files.list(dir)) {
            return listing
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .sorted()
                .toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path locateCorpus(final String corpusSubdir) {
        final var resource = OracleCorpus.class.getClassLoader().getResource("corpus/" + corpusSubdir);
        if (resource == null) {
            throw new IllegalStateException("corpus directory not found on classpath: corpus/" + corpusSubdir);
        }
        try {
            return Paths.get(resource.toURI());
        } catch (final URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String readString(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String simpleName(final String className) {
        final var dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private OracleCorpus() {}
}
