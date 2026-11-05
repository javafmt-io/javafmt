package io.javafmt.spotless;

import com.diffplug.spotless.FormatterFunc;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Lint;
import io.javafmt.Diagnostic;
import io.javafmt.FormatResult;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.JavafmtVersion;
import io.javafmt.Position;
import io.javafmt.ProjectFile;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@UtilityClass
public final class JavafmtFormatterStep {
    private final String NAME = "javafmt";

    public FormatterStep create(final File projectDirectory) {
        return create(JavafmtConfig.defaults(), projectDirectory);
    }

    public FormatterStep create(final JavafmtConfig config, final File projectDirectory) {
        return createForVersion(config, projectDirectory, JavafmtVersion.VERSION);
    }

    /**
     * A step that formats with semantic analysis, so the lint rules that need resolved types are
     * live — {@code RedundantTypeArguments}' type witnesses, {@code ExplodeStarImports} and the
     * rest. {@code analysisClasspath} is the compile classpath of the sources being formatted, as
     * {@code javac -classpath} would take it. Every step keys Spotless's up-to-date check on the
     * {@code .javafmt} project files that apply to {@code projectDirectory} and the directories
     * below it; each formatted file uses the project file that applies to it.
     */
    public FormatterStep create(final JavafmtConfig config, final String analysisClasspath, final File projectDirectory) {
        Objects.requireNonNull(analysisClasspath, "analysisClasspath");
        return create(config.withAnalysis(new JavafmtConfig.Analysis.Attributed(analysisClasspath)), projectDirectory);
    }

    /**
     * Package-private seam so tests can build steps that differ only in formatter version; every
     * production caller gets the build-stamped {@link JavafmtVersion#VERSION}.
     */
    FormatterStep createForVersion(final JavafmtConfig config, final File projectDirectory, final String formatterVersion) {
        Objects.requireNonNull(config, "config");
        final var directory = Objects.requireNonNull(projectDirectory, "projectDirectory").getAbsolutePath();
        return FormatterStep.createLazy(NAME, () -> new Setup(formatterVersion, config, directory), Setup::state, State::formatter);
    }

    public String apply(final String source) {
        return apply(source, JavafmtConfig.defaults());
    }

    public String apply(final String source, final JavafmtConfig config) {
        return throwOnError(Javafmt.formatWithResult(source, config));
    }

    public String apply(final String source, final File file, final String classpath, final int release) {
        final var config = JavafmtConfig.defaults().withRelease(release);
        return throwOnError(
                Javafmt.formatWithResult(
                        source, classpath.isEmpty() ? config : config.withAnalysis(new JavafmtConfig.Analysis.Attributed(classpath)),
                        ProjectFile.forFile(file.toPath())));
    }

    /** Package-private seam so tests can assert on the thrown message directly. */
    String throwOnError(final FormatResult result) {
        if (result.hasErrors()) {
            throw new IllegalArgumentException("javafmt: " + result.errors().getFirst().describeBare());
        }
        return result.output();
    }

    private ProjectFile projectFileFor(final @Nullable File file) {
        return file == null ? ProjectFile.none() : ProjectFile.forFile(file.toPath());
    }

    /**
     * Renders one javafmt warning as a Spotless lint. The short code is the step name rather than
     * a rule name because a {@link Diagnostic.Warning} carries only a message — javafmt's
     * warning-only rules do not tag their diagnostics with the rule that raised them.
     */
    private Lint toLint(final Diagnostic warning) {
        return switch (warning.position()) {
            case Position.At(var line, var _, var _) -> Lint.atLine(line, NAME, warning.message());
            case Position.Unknown() -> Lint.atUndefinedLine(NAME, warning.message());
        };
    }

    record Func(JavafmtConfig config) implements FormatterFunc {
        Func {
            Objects.requireNonNull(config, "config");
        }

        @Override
        public String apply(final String input) {
            return throwOnError(Javafmt.formatWithResult(input, config));
        }

        @Override
        public String apply(final String unix, final @Nullable File file) {
            return throwOnError(Javafmt.formatWithResult(unix, config, projectFileFor(file)));
        }

        @Override
        public List<Lint> lint(final String content, final @Nullable File file) {
            return Javafmt.formatWithResult(content, config, projectFileFor(file)).warnings().stream().map(JavafmtFormatterStep::toLint).toList();
        }
    }

    record Setup(String formatterVersion, JavafmtConfig config, String projectDirectory) implements Serializable {
        Setup {
            Objects.requireNonNull(formatterVersion, "formatterVersion");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(projectDirectory, "projectDirectory");
        }

        State state() {
            return new State(formatterVersion, config, ProjectFiles.under(Path.of(projectDirectory)));
        }
    }

    /**
     * Serialized step state. Spotless decides whether a file is already formatted by comparing the
     * serialized bytes of this record, so every component here participates in cache invalidation.
     *
     * @param formatterVersion the javafmt release that will do the formatting. A javafmt upgrade can
     *     change what {@code format(x)} produces; without the version in the state, Spotless would
     *     keep reporting files formatted by an older release as up to date and never reformat them.
     * @param config the formatter configuration, so a config change invalidates too. It includes the
     *     analysis classpath, which decides which lint rules can fire.
     * @param projectFiles every {@code .javafmt} that can apply to a file under the project
     *     directory, and every repository root that ends the search for one, read again on every
     *     build, since a project file decides which rules run and whether a file formats at all.
     */
    record State(String formatterVersion, JavafmtConfig config, ProjectFiles projectFiles) implements Serializable {
        State {
            Objects.requireNonNull(formatterVersion, "formatterVersion");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(projectFiles, "projectFiles");
        }

        FormatterFunc formatter() {
            return new Func(config);
        }
    }
}
