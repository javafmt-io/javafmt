package io.javafmt.lint.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.CompileCheck;
import io.javafmt.Diagnostic;
import io.javafmt.FormatResult;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
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
 * assert that javafmt's output is Checkstyle-clean for the corresponding rule.
 *
 * <p>Two assertions matter for an oracle suite:
 * <ul>
 *   <li><b>Soundness</b> ({@link #assertJavafmtMakesItClean}): javafmt's output must not contain
 *       any violations the oracle would flag. This proves javafmt doesn't miss cases.</li>
 *   <li><b>Coverage</b> ({@link #assertCorpusExercisesRule}): at least one corpus file must
 *       have produced violations originally. Without this, soundness is vacuously satisfied
 *       by a corpus of already-clean files.</li>
 * </ul>
 */
public final class OracleCorpus {

    private static final JavafmtConfig VERIFYING = JavafmtConfig.defaults().withVerifyOutput(true);

    public static Stream<Arguments> load(final String corpusSubdir) {
        final var dir = locateCorpus(corpusSubdir);
        try (final var listing = Files.list(dir)) {
            return listing.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .<Arguments>map(p -> Arguments.of(p.getFileName().toString(), readString(p)))
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void assertJavafmtMakesItClean(
            final String name,
            final String source,
            final String checkClassName,
            final Map<String, String> checkProperties) {
        Objects.requireNonNull(name, "name");
        final var formatted = formatVerified(name, source);
        assertStillCompiles(name, source, formatted);
        final var violations = CheckstyleOracle.run(formatted, checkClassName, checkProperties);
        assertThat(violations)
                .as("javafmt output for corpus file '%s' should have zero %s violations; output:%n%s", name, simpleName(checkClassName), formatted)
                .isEmpty();
    }

    /**
     * {@link #assertJavafmtMakesItClean} for rules that only fire with attribution. The corpus is
     * attributed against javafmt's own test classpath, which covers the JDK — enough for corpora
     * that reference only {@code java.*} types.
     */
    public static void assertJavafmtWithAnalyzeMakesItClean(
            final String name,
            final String source,
            final String checkClassName,
            final Map<String, String> checkProperties) {
        Objects.requireNonNull(name, "name");
        final var result = Javafmt.formatWithAnalyze(source, System.getProperty("java.class.path"), VERIFYING);
        assertNoInternalError(name, result);
        final var formatted = result.output();
        assertStillCompiles(name, source, formatted);
        final var violations = CheckstyleOracle.run(formatted, checkClassName, checkProperties);
        assertThat(violations)
                .as(
                        "javafmt --analyze output for corpus file '%s' should have zero %s violations; output:%n%s",
                        name,
                        simpleName(checkClassName),
                        formatted)
                .isEmpty();
    }

    /**
     * For rules that may either auto-fix or warn-only: every Checkstyle violation javafmt
     * doesn't fix away on output must be matched by at least one javafmt diagnostic whose
     * message contains {@code diagnosticMessageSubstring}. The match is by message
     * substring, not subtype, since callers care that javafmt reported <em>something</em>
     * about the case it didn't silently rewrite.
     */
    public static void assertJavafmtFixesOrReports(
            final String name,
            final String source,
            final String diagnosticMessageSubstring,
            final String checkClassName,
            final Map<String, String> checkProperties) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(diagnosticMessageSubstring, "diagnosticMessageSubstring");
        final var result = Javafmt.formatWithResult(source, VERIFYING);
        final var formatted = result.output();
        assertNoInternalError(name, result);
        assertStillCompiles(name, source, formatted);
        final var violations = CheckstyleOracle.run(formatted, checkClassName, checkProperties);
        if (violations.isEmpty()) {
            return;
        }
        final var hasMatchingDiagnostic = result.diagnostics()
                .stream()
                .anyMatch(d -> d.message().contains(diagnosticMessageSubstring));
        assertThat(hasMatchingDiagnostic)
                .as(
                        "javafmt output for corpus file '%s' still has %s violations and no diagnostic "
                            + "containing '%s'; output:%n%s%nresidual violations: %s%njavafmt diagnostics: %s",
                        name,
                        simpleName(checkClassName),
                        diagnosticMessageSubstring,
                        formatted,
                        violations,
                        result.diagnostics())
                .isTrue();
    }

    public static void assertCorpusExercisesRule(final String corpusSubdir, final String checkClassName, final Map<String, String> checkProperties) {
        final var dirty = corpusFiles(corpusSubdir)
                .stream()
                .filter(p -> !CheckstyleOracle.run(readString(p), checkClassName, checkProperties).isEmpty())
                .map(p -> p.getFileName().toString())
                .toList();
        assertThat(dirty)
                .as("oracle corpus '%s' must contain at least one file with %s violations "
                        + "(otherwise soundness assertions pass vacuously)", corpusSubdir, simpleName(checkClassName))
                .isNotEmpty();
    }

    private static String formatVerified(final String name, final String source) {
        final var result = Javafmt.formatWithResult(source, VERIFYING);
        assertNoInternalError(name, result);
        return result.output();
    }

    private static void assertNoInternalError(final String name, final FormatResult result) {
        final var internal = result.diagnostics()
                .stream()
                .filter(d -> d instanceof Diagnostic.InternalError)
                .map(Diagnostic::message)
                .toList();
        assertThat(internal)
                .as("corpus file '%s' must format without an internal error", name)
                .isEmpty();
    }

    /**
     * A lint auto-fix that produces syntactically valid but non-compiling code — a stripped
     * {@code final} initializer, a {@code var} that widened an inferred type — only shows up
     * under a real compile. Differential, so a corpus file that never compiled on its own
     * isn't blamed on javafmt.
     */
    private static void assertStillCompiles(final String name, final String source, final String formatted) {
        if (!(CompileCheck.check(source) instanceof CompileCheck.Outcome.Compiles)) {
            return;
        }
        assertThat(CompileCheck.check(formatted))
                .as("corpus file '%s' compiled before formatting but not after; output:%n%s", name, formatted)
                .isNotInstanceOf(CompileCheck.Outcome.Broken.class);
    }

    /**
     * Non-vacuity for {@link #assertStillCompiles}, mirroring {@link #assertCorpusExercisesRule}:
     * a corpus of snippets that all reference off-classpath types would skip every compile
     * check and pass without ever compiling anything.
     */
    public static void assertCorpusIsCompilable(final String corpusSubdir) {
        final var compilable = corpusFiles(corpusSubdir)
                .stream()
                .filter(p -> CompileCheck.check(readString(p)) instanceof CompileCheck.Outcome.Compiles)
                .map(p -> p.getFileName().toString())
                .toList();
        assertThat(compilable)
                .as("oracle corpus '%s' must contain at least one self-contained file that compiles "
                        + "(otherwise the compile assertion is skipped for every file)", corpusSubdir)
                .isNotEmpty();
    }

    /**
     * Every corpus directory name, so a suite that has to cover all of them cannot fall behind a
     * newly added corpus.
     */
    public static Stream<String> corpusNames() {
        final var resource = OracleCorpus.class.getClassLoader().getResource("corpus");
        if (resource == null) {
            throw new IllegalStateException("corpus root not found on classpath: corpus/");
        }
        try (final var listing = Files.list(Paths.get(resource.toURI()))) {
            return listing.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Path> corpusFiles(final String corpusSubdir) {
        final var dir = locateCorpus(corpusSubdir);
        try (final var listing = Files.list(dir)) {
            return listing.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
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
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String readString(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String simpleName(final String className) {
        final var dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private OracleCorpus() {}
}
