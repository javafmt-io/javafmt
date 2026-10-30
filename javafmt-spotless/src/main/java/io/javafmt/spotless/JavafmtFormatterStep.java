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
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class JavafmtFormatterStep {

    private final String NAME = "javafmt";

    public FormatterStep create() {
        return create(JavafmtConfig.defaults());
    }

    public FormatterStep create(final JavafmtConfig config) {
        return create(config, "");
    }

    /**
     * A step that formats with semantic analysis, so the lint rules that need resolved types are
     * live — {@code RedundantTypeArguments}' type witnesses, {@code ExplodeStarImports} and the
     * rest. {@code analysisClasspath} is the compile classpath of the sources being formatted, as
     * {@code javac -cp} would take it; an empty string leaves {@code config.analysis()} as given,
     * which is what {@link #create(JavafmtConfig)} uses.
     *
     * <p>Without this overload, the only way to reach analysis was javafmt's own build, so javafmt
     * formatted itself with strictly more rules than any user of the published step could enable.
     */
    public FormatterStep create(final JavafmtConfig config, final String analysisClasspath) {
        return createForVersion(config, analysisClasspath, JavafmtVersion.VERSION);
    }

    /**
     * Package-private seam so tests can build steps that differ only in formatter version; every
     * production caller gets the build-stamped {@link JavafmtVersion#VERSION}. Named apart from
     * {@code create} because {@code create(config, String)} is the public analysis-classpath
     * overload, and two same-shaped methods meaning different things is a trap inside the package.
     */
    FormatterStep createForVersion(final JavafmtConfig config, final String analysisClasspath, final String formatterVersion) {
        final var analyzed = analysisClasspath.isEmpty() ? config : config.withAnalysis(new JavafmtConfig.Analysis.Attributed(analysisClasspath));
        return FormatterStep.create(NAME, new State(formatterVersion, analyzed), state -> new Func(state.config()));
    }

    public String apply(final String source) {
        return apply(source, JavafmtConfig.defaults());
    }

    /**
     * Runs semantic analysis when possible, falling back to a syntax-only parse when it fails.
     * Attribution or rendering can fail (e.g. ERRONEOUS nodes from unresolvable types on an
     * incomplete classpath); parse-only formatting lets callers with a partial classpath degrade
     * gracefully rather than breaking the formatter outright.
     */
    public String applyWithAnalyze(final String source, final String classpath) {
        return applyWithAnalyze(source, classpath, JavafmtConfig.defaults());
    }

    /** {@link #applyWithAnalyze(String, String)} with an explicit configuration. */
    public String applyWithAnalyze(final String source, final String classpath, final JavafmtConfig config) {
        return throwOnError(resultFor(source, config.withAnalysis(new JavafmtConfig.Analysis.Attributed(classpath))));
    }

    public String apply(final String source, final JavafmtConfig config) {
        return throwOnError(Javafmt.formatWithResult(source, config));
    }

    /** Package-private seam so tests can assert on the thrown message directly. */
    String throwOnError(final FormatResult result) {
        if (result.hasErrors()) {
            throw new IllegalArgumentException("javafmt: " + result.errors().getFirst().describeBare());
        }
        return result.output();
    }

    /**
     * The full result for {@code config}. With semantic analysis, a result with errors is replaced
     * by the parse-only one, for both the formatted output and the lint pass's warnings.
     */
    private FormatResult resultFor(final String source, final JavafmtConfig config) {
        final var result = Javafmt.formatWithResult(source, config);
        return result.hasErrors() && config.analysis() instanceof JavafmtConfig.Analysis.Attributed
                ? Javafmt.formatWithResult(source, config.withAnalysis(new JavafmtConfig.Analysis.ParseOnly()))
                : result;
    }

    /**
     * Renders one javafmt warning as a Spotless lint. The short code is the step name rather than
     * a rule name because a {@link Diagnostic.Warning} carries only a message — javafmt's
     * warning-only rules do not tag their diagnostics with the rule that raised them.
     */
    private Lint toLint(final Diagnostic warning) {
        return switch (warning.position()) {
            case Position.At(var line, var ignoredColumn, var ignoredOffset) -> Lint.atLine(line, NAME, warning.message());
            case Position.Unknown() -> Lint.atUndefinedLine(NAME, warning.message());
        };
    }

    record Func(JavafmtConfig config) implements FormatterFunc {

        Func {
            Objects.requireNonNull(config, "config");
        }

        @Override
        public String apply(final String input) {
            return throwOnError(resultFor(input, config));
        }

        @Override
        public List<Lint> lint(final String content, final File file) {
            return resultFor(content, config).warnings().stream().map(JavafmtFormatterStep::toLint).toList();
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
    record State(String formatterVersion, JavafmtConfig config) implements Serializable {

        State {
            Objects.requireNonNull(formatterVersion, "formatterVersion");
            Objects.requireNonNull(config, "config");
        }
    }
}
