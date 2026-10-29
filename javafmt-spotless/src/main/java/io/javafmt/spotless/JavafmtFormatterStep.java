package io.javafmt.spotless;

import com.diffplug.spotless.FormatterFunc;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Lint;
import io.javafmt.Diagnostic;
import io.javafmt.FormatResult;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.JavafmtVersion;
import io.javafmt.PathFilter;
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
     * {@code javac -cp} would take it; an empty string means parse-only, which is what
     * {@link #create(JavafmtConfig)} uses.
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
        final var state = new State(formatterVersion, config, analysisClasspath);
        return new LintingStep(FormatterStep.create(NAME, state, JavafmtFormatterStep::buildFunc), state);
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
        final var result = Javafmt.formatWithAnalyze(source, classpath, config);
        return result.hasErrors() ? apply(source, config) : result.output();
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
     * Build the per-state {@link FormatterFunc}. When {@code excludes} is empty we return a plain
     * (file-agnostic) func so the step state stays cheap and serialization-stable. Otherwise we
     * return a {@link FormatterFunc.NeedsFile} that consults the path on each call via {@link
     * PathFilter}, matched against the full path and the bare filename — a match in either skips
     * the file.
     */
    private FormatterFunc buildFunc(final State state) {
        if (state.config().excludes().isEmpty()) {
            return input -> format(input, state);
        }
        return (FormatterFunc.NeedsFile) (input, file) -> isExcluded(state.config(), file) ? input : format(input, state);
    }

    /** Formats one source the way {@code state} asks for: with semantic analysis when it carries a classpath. */
    private String format(final String source, final State state) {
        return state.analysisClasspath().isEmpty()
                ? apply(source, state.config())
                : applyWithAnalyze(source, state.analysisClasspath(), state.config());
    }

    /** The full result for {@code state}, for the lint pass, which needs the warnings rather than the output. */
    private FormatResult resultFor(final String source, final State state) {
        return state.analysisClasspath().isEmpty()
                ? Javafmt.formatWithResult(source, state.config())
                : Javafmt.formatWithAnalyze(source, state.analysisClasspath(), state.config());
    }

    private boolean isExcluded(final JavafmtConfig cfg, final File file) {
        return file != null
            && !cfg.excludes().isEmpty()
            && PathFilter.ofGlobs(cfg.excludes()).excludes(file.toPath());
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

    /**
     * The published step: {@code format} is the delegate Spotless built, and {@code lint} reports
     * javafmt's warning-only rules — {@code FallThrough}, {@code EqualsHashCode},
     * {@code MissingSwitchDefault}, {@code AvoidStarImport} and the rest.
     *
     * <p>Those rules are a headline feature, and Gradle via Spotless was the one integration where
     * they did nothing: {@code apply} throws on errors and dropped {@code warnings()} on the floor,
     * so no Gradle user ever saw one. {@code FormatterStep.lint} is where Spotless asks for them.
     *
     * <p>Equality and serialization are the delegate's, so up-to-date checking still keys on the
     * javafmt version and the config exactly as before; {@code config} rides along only so
     * {@code lint} can format with the same settings.
     */
    record LintingStep(FormatterStep delegate, State state) implements FormatterStep {

        LintingStep {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(state, "state");
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String format(final String rawUnix, final File file) throws Exception {
            return delegate.format(rawUnix, file);
        }

        @Override
        public List<Lint> lint(final String content, final File file) {
            if (isExcluded(state.config(), file)) {
                return List.of();
            }
            return resultFor(content, state)
                    .warnings()
                    .stream()
                    .map(JavafmtFormatterStep::toLint)
                    .toList();
        }

        @Override
        public void close() {
            try {
                delegate.close();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Serialized step state. Spotless decides whether a file is already formatted by comparing the
     * serialized bytes of this record, so every component here participates in cache invalidation.
     *
     * @param formatterVersion the javafmt release that will do the formatting. A javafmt upgrade can
     *     change what {@code format(x)} produces; without the version in the state, Spotless would
     *     keep reporting files formatted by an older release as up to date and never reformat them.
     * @param config the formatter configuration, so a config change invalidates too.
     * @param analysisClasspath the classpath semantic analysis runs against, empty for parse-only.
     *     It decides which lint rules can fire, so a changed classpath has to invalidate as well.
     */
    record State(String formatterVersion, JavafmtConfig config, String analysisClasspath) implements Serializable {

        State {
            Objects.requireNonNull(formatterVersion, "formatterVersion");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(analysisClasspath, "analysisClasspath");
        }
    }
}
