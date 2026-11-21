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
        return createForVersion(JavafmtVersion.VERSION, config, projectDirectory);
    }

    /**
     * A step that formats with semantic analysis, so the lint rules that need resolved types are
     * live — {@code RedundantTypeArguments}' type witnesses, {@code ExplodeStarImports} and the
     * rest. {@code analysisClasspath} is the compile classpath of the sources being formatted, as
     * {@code javac -classpath} would take it; an empty string leaves {@code config.analysis()} as given.
     */
    public FormatterStep create(final JavafmtConfig config, final String analysisClasspath, final File projectDirectory) {
        Objects.requireNonNull(analysisClasspath, "analysisClasspath");
        return create(analysisFor(config, analysisClasspath), projectDirectory);
    }

    /**
     * Package-private seam so tests can build steps that differ only in formatter version; every
     * production caller gets the build-stamped {@link JavafmtVersion#VERSION}.
     */
    FormatterStep createForVersion(final String formatterVersion, final JavafmtConfig config, final File projectDirectory) {
        final var directory = Objects.requireNonNull(projectDirectory, "projectDirectory")
                .getAbsolutePath();
        return FormatterStep.create(NAME, new Setup(formatterVersion, config, directory), Setup::state, State::formatter);
    }

    public String apply(final String source, final File file, final String classpath, final int release) {
        final var config = JavafmtConfig.defaults().withRelease(release);
        return new Func(analysisFor(config, classpath)).apply(source, file);
    }

    private JavafmtConfig analysisFor(final JavafmtConfig config, final String classpath) {
        return classpath.isEmpty() ? config : config.withAnalysis(new JavafmtConfig.Analysis.Attributed(classpath));
    }

    private String throwOnError(final FormatResult result) {
        if (result.hasErrors()) {
            throw new IllegalArgumentException(
                    "javafmt: " + result.errors().getFirst().describeBare());
        }
        return result.output();
    }

    record Func(JavafmtConfig config) implements FormatterFunc {

        Func {
            Objects.requireNonNull(config, "config");
        }

        @Override
        public String apply(final String source) {
            return throwOnError(Javafmt.formatWithResult(source, config));
        }

        @Override
        public String apply(final String source, final @Nullable File file) {
            return throwOnError(Javafmt.formatWithResult(source, config, projectFileFor(file)));
        }

        @Override
        public List<Lint> lint(final String source, final @Nullable File file) {
            return Javafmt.formatWithResult(source, config, projectFileFor(file))
                    .warnings()
                    .stream()
                    .map(Func::toLint)
                    .toList();
        }

        private static ProjectFile projectFileFor(final @Nullable File file) {
            return file == null ? ProjectFile.none() : ProjectFile.forFile(file.toPath());
        }

        private static Lint toLint(final Diagnostic warning) {
            return switch (warning.position()) {
                case Position.At(var line, var _, var _) -> Lint.atLine(line, NAME, warning.message());
                case Position.Unknown() -> Lint.atUndefinedLine(NAME, warning.message());
            };
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
