package io.javafmt;

import io.javafmt.builder.DocBuilder;
import io.javafmt.lint.LintContext;
import io.javafmt.lint.LintEngine;
import io.javafmt.lint.LintEngine.Outcome;
import io.javafmt.lint.LintRules;
import io.javafmt.parser.AttributedOutcome;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParseException;
import io.javafmt.parser.ParseOutcome;
import io.javafmt.parser.ParsedUnit;
import io.javafmt.parser.RoundTripVerifier;
import io.javafmt.printer.PrintStrategy;
import io.javafmt.printer.Printer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The entry point for formatting Java source with javafmt. Every overload here formats one
 * source string in isolation; use {@link #formatAll} to format many sources at once and share
 * javac task setup across them.
 *
 * <p>Formatting never throws for input that fails to parse or that javafmt itself chokes on:
 * every overload returns a result (either the formatted text directly, via {@link #format}, or
 * a {@link FormatResult} carrying diagnostics) and, on failure, hands back the original source
 * unchanged with the failure recorded as a {@link Diagnostic}.
 */
@UtilityClass
public final class Javafmt {

    private final int LINE_WIDTH = 150;

    /** Where a refusal tells the user to send the file that triggered it. */
    private final String ISSUE_TRACKER = "https://github.com/javafmt-io/javafmt/issues";

    final LintEngine LINT_ENGINE = new LintEngine(LintRules.defaults());

    /** {@link #format(String)} with an explicit configuration. */
    public String format(final String source, final JavafmtConfig config) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(config, "config");
        return formatWithResult(source, config).output();
    }

    /**
     * Formats {@code source} and returns the formatted text. On failure, returns {@code source}
     * unchanged; use {@link #formatWithResult(String)} if the caller needs to know why.
     */
    public String format(final String source) {
        Objects.requireNonNull(source, "source");
        return format(source, JavafmtConfig.defaults());
    }

    /** {@link #formatWithResult(String, JavafmtConfig)} with the default configuration. */
    public FormatResult formatWithResult(final String source) {
        Objects.requireNonNull(source, "source");
        return formatWithResult(source, JavafmtConfig.defaults());
    }

    /** Formats {@code source}, returning both the output and any diagnostics raised. */
    public FormatResult formatWithResult(final String source, final JavafmtConfig config) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(config, "config");
        return formatWithResult(source, config, PrintStrategy.wadlerLindig());
    }

    /**
     * Formats {@code source} using a pre-computed parse {@code outcome}, intended for callers
     * that batch-parsed via {@link JavaParser#parseUnits(List)} and want to amortize the
     * per-task setup across many files. Failed outcomes round-trip the source unchanged with
     * the parse diagnostics attached.
     */
    public FormatResult formatWithResult(final String source, final ParseOutcome outcome) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(outcome, "outcome");
        return formatWithResult(source, outcome, JavafmtConfig.defaults());
    }

    public FormatResult formatWithResult(final String source, final ParseOutcome outcome, final JavafmtConfig config) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(config, "config");
        return switch (outcome) {
            case ParseOutcome.Ok ok -> formatParsed(source, ok.unit(), config);
            case ParseOutcome.Failed failed -> parseExceptionToResult(source, failed.error());
        };
    }

    FormatResult formatWithResult(final String source, final JavafmtConfig config, final PrintStrategy strategy) {
        if (source.isEmpty()) {
            return new FormatResult(source, List.of());
        }
        final var encoding = SourceEncoding.of(source);
        final var normalized = encoding.normalize(source);
        final ParsedUnit unit;
        try {
            unit = JavaParser.parseUnit(normalized);
        } catch (ParseException e) {
            return asWritten(encoding, parseExceptionToResult(normalized, e));
        } catch (RuntimeException | AssertionError | StackOverflowError t) {
            return asWritten(encoding, internalErrorResult(normalized, t));
        }
        return asWritten(encoding, formatParsed(normalized, unit, config, strategy));
    }

    /**
     * Puts the input's BOM and line ending back on a result. Everything between the parser and the
     * printer works in LF with no BOM; writing that out verbatim would rewrite every line of a CRLF
     * file and bury the real change in a whole-file diff.
     */
    FormatResult asWritten(final SourceEncoding encoding, final FormatResult result) {
        return new FormatResult(encoding.restore(result.output()), result.diagnostics());
    }

    FormatResult formatParsed(final String source, final ParsedUnit unit, final JavafmtConfig config) {
        return formatParsed(source, unit, config, PrintStrategy.wadlerLindig());
    }

    FormatResult formatParsed(final String source, final ParsedUnit unit, final JavafmtConfig config, final PrintStrategy strategy) {
        return formatWithContext(source, LintContext.parseOnly(unit), config, strategy);
    }

    /**
     * Formats {@code source} after running javac attribution with {@code classpath}, so that
     * attribution-requiring lint rules see resolved type information. Mirrors what the CLI's
     * {@code --analyze --classpath ...} flow does end-to-end for a single source. Parse
     * failures surface as diagnostics on the returned {@link FormatResult}; the source is
     * round-tripped unchanged.
     *
     * <p>If attribution itself fails, formatting still succeeds parse-only, but a
     * {@link Diagnostic.Warning} is attached: the caller asked for {@code --analyze} and did
     * not get it, since the rules that need resolved types quietly did nothing, and the result
     * should say so rather than look like a full analyze pass.
     */
    public FormatResult formatWithAnalyze(final String source, final String classpath) {
        return formatWithAnalyze(source, classpath, JavafmtConfig.defaults());
    }

    /** {@link #formatWithAnalyze(String, String)} with an explicit configuration. */
    public FormatResult formatWithAnalyze(final String source, final String classpath, final JavafmtConfig config) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(classpath, "classpath");
        Objects.requireNonNull(config, "config");
        if (source.isEmpty()) {
            return new FormatResult(source, List.of());
        }
        final var encoding = SourceEncoding.of(source);
        final var normalized = encoding.normalize(source);
        try {
            final var attributed = JavaParser.parseAndAttribute(normalized, classpath);
            return asWritten(encoding, formatWithContext(normalized, LintContext.attributed(attributed), config, PrintStrategy.wadlerLindig()));
        } catch (ParseException e) {
            return asWritten(encoding, parseExceptionToResult(normalized, e));
        } catch (RuntimeException e) {
            return withAttributionWarning(formatWithResult(source, config), e);
        }
    }

    /** Appends the "attribution unavailable" warning to an otherwise parse-only result. */
    private FormatResult withAttributionWarning(final FormatResult result, final RuntimeException cause) {
        return withWarnings(result, List.of(attributionWarning(cause)));
    }

    FormatResult withWarnings(final FormatResult result, final List<Diagnostic> warnings) {
        return warnings.isEmpty()
                ? result
                : new FormatResult(result.output(), Stream.concat(result.diagnostics().stream(), warnings.stream()).toList());
    }

    Diagnostic attributionWarning(final RuntimeException cause) {
        return new Diagnostic.Warning(
                "--analyze requested but attribution failed (" + cause.getClass().getSimpleName() + ": " + cause.getMessage()
                        + "); formatted without the rules that need resolved types",
                new Position.Unknown());
    }

    /**
     * Formats {@code source} from a pre-computed {@link AttributedOutcome}, intended for
     * callers that batch-attributed via {@link JavaParser#parseAndAttribute(List, String)}
     * to amortize task setup across many files. Failed outcomes round-trip the source
     * unchanged with the failure attached as a diagnostic.
     */
    public FormatResult formatWithResult(final String source, final AttributedOutcome outcome) {
        return formatWithResult(source, outcome, JavafmtConfig.defaults());
    }

    /** {@link #formatWithResult(String, AttributedOutcome)} with an explicit configuration. */
    public FormatResult formatWithResult(final String source, final AttributedOutcome outcome, final JavafmtConfig config) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(config, "config");
        return switch (outcome) {
            case AttributedOutcome.Ok ok -> formatWithContext(source, LintContext.attributed(ok.unit()), config, PrintStrategy.wadlerLindig());
            case AttributedOutcome.Failed failed -> failureToResult(source, failed.error());
        };
    }

    /**
     * Formats many sources at once, sharing javac task setup across them. Result {@code i}
     * corresponds to {@code sources.get(i)} and is identical to what {@link #format(String,
     * JavafmtConfig)} returns for that source alone — only the cost differs.
     *
     * <p>Prefer this over a loop whenever the whole file list is known up front. Task setup is
     * ~98% of a single-file parse, and the lint pass re-parses each file it rewrites, so a
     * per-file loop pays that setup several times per file. Batching a dirty corpus measured ~4x
     * faster than formatting the same files one at a time.
     *
     * <p>Sources are processed in chunks so peak memory stays bounded by one chunk's syntax trees
     * rather than the whole run. Blank sources are passed straight through without occupying a
     * parse slot, which lets callers keep placeholder entries for files they resolved some other
     * way (a cache hit, or a read error) and stay index-aligned.
     *
     * @param threads how many threads render in parallel; values below 1 are treated as 1
     */
    public List<FormatResult> formatAll(final List<String> sources, final JavafmtConfig config, final int threads) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(config, "config");
        return BatchFormatter.formatAll(sources, config, threads);
    }

    /**
     * {@link #formatAll} with javac attribution, for the rules that need resolved types.
     *
     * <p>Unlike {@link #formatAll} this does <em>not</em> chunk: every source is attributed in one
     * task so that cross-file references resolve. Splitting the run would leave a type declared in
     * one chunk unresolvable from another, and rules that key on resolved symbols — {@code
     * ExplodeStarImports} in particular — deliberately do nothing when a name will not resolve.
     * Peak memory is therefore proportional to the whole run.
     */
    public List<FormatResult> formatAllWithAnalyze(
            final List<String> sources,
            final String classpath,
            final JavafmtConfig config,
            final int threads) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(classpath, "classpath");
        Objects.requireNonNull(config, "config");
        return BatchFormatter.formatAllWithAnalyze(sources, classpath, config, threads);
    }

    FormatResult formatWithContext(final String source, final LintContext ctx, final JavafmtConfig config, final PrintStrategy strategy) {
        try {
            return render(source, lintIfEnabled(ctx, config), config, strategy);
        } catch (RuntimeException | AssertionError | StackOverflowError t) {
            return internalErrorResult(source, t);
        }
    }

    /**
     * Runs the lint pass unless {@link JavafmtConfig#lint()} turned it off, in which case the parsed
     * unit passes straight to rendering. An attributed unit is still re-parsed so the renderer never
     * sees javac-injected synthetic types.
     */
    Outcome lintIfEnabled(final LintContext ctx, final JavafmtConfig config) {
        if (config.lint()) {
            return LINT_ENGINE.lint(ctx);
        }
        final var unit = ctx.attribution().isPresent() ? JavaParser.parseUnit(ctx.unit().source()) : ctx.unit();
        return new Outcome(unit, List.of());
    }

    /**
     * Builds and prints one already-linted unit. Split out of {@link #formatWithContext} so the
     * batch pipeline, whose lint stage runs across many files at once, shares this stage instead
     * of reimplementing it.
     */
    FormatResult render(final String source, final LintEngine.Outcome lintOutcome, final JavafmtConfig config, final PrintStrategy strategy) {
        try {
            final var built = DocBuilder.buildWithFallbacks(lintOutcome.unit(), config);
            final var printed = new Printer(LINE_WIDTH, strategy).print(built.doc());
            final var trimmed = printed.replaceFirst("\\n+$", "");
            final var output = trimmed.isEmpty() ? "" : trimmed + "\n";
            final var linted = lintOutcome.unit().source();
            final var mismatch = verifyOutput(config, source, linted, output);
            if (mismatch.isPresent()) {
                return new FormatResult(source, List.of(new Diagnostic.InternalError(mismatch.get(), whereItDiverged(linted, output))));
            }
            final var diagnostics = new ArrayList<Diagnostic>(built.diagnostics());
            diagnostics.addAll(lintOutcome.diagnostics());
            return new FormatResult(output, diagnostics);
        } catch (RuntimeException | AssertionError | StackOverflowError t) {
            return internalErrorResult(source, t);
        }
    }

    /** javafmt itself failed on source that parsed; the input is returned untouched. */
    FormatResult internalErrorResult(final String source, final Throwable t) {
        final var message = t.getMessage();
        return new FormatResult(
                source,
                List.of(
                        new Diagnostic.InternalError(
                                t.getClass().getSimpleName() + ": " + (message == null ? t.toString() : message),
                                new Position.Unknown())));
    }

    /**
     * Compares the printed output against the source the renderer was handed — the post-lint
     * source, since lint rewrites are deliberate and already validated by the lint engine's
     * re-parse. Anything the Doc build and print stage changed beyond layout is a javafmt bug.
     *
     * <p>Comments are held against {@code original} instead, the source the user actually wrote.
     * No lint rule is allowed to delete a comment, and checking against the post-lint source
     * cannot tell when one did: the comment is missing from both sides of that comparison.
     */
    Optional<String> verifyOutput(final JavafmtConfig config, final String original, final String linted, final String output) {
        if (!config.verifyOutput()) {
            return Optional.empty();
        }
        return RoundTripVerifier.verify(linted, output)
                .map(m -> refusal("formatting this file changed more than its layout", m))
                .or(() -> RoundTripVerifier.verifyComments(original, output).map(m -> refusal("a lint rule dropped one of your comments", m)));
    }

    /**
     * Where the output first stops matching the source, so a refusal can name a line instead of
     * only a file. The offset is into the post-lint source rather than the user's own text, so a
     * file the lint pass rewrote heavily can report a line a little off; that is worth far more
     * than the nothing it reported before. Empty when only the comments differ, which the token
     * comparison cannot locate.
     */
    private Position whereItDiverged(final String linted, final String output) {
        final var offset = RoundTripVerifier.firstDifferingOffset(linted, output);
        if (offset.isEmpty()) {
            return new Position.Unknown();
        }
        final var at = offset.getAsInt();
        final var line = (int) linted.substring(0, at).chars().filter(c -> c == '\n').count() + 1;
        return new Position.At(line, at - (linted.lastIndexOf('\n', at - 1) + 1) + 1, at);
    }

    /**
     * The message a refusal carries. It has three jobs, and the one it used to do was the least
     * important of them: saying which tokens differ. First it has to say whose fault this is —
     * reaching here means javafmt got it wrong, not that there is anything wrong with the file, and
     * a bare {@code error:} in a build log reads as the opposite. Second, that the file was left
     * exactly as it was, so nobody goes looking for damage. Only then the detail, which is for
     * whoever fixes the bug rather than for whoever hit it, and the place to send it.
     */
    private String refusal(final String what, final String detail) {
        return "javafmt bug: " + what + ", so the file was left unchanged and nothing was written — " + detail
                + ". Your code is fine; please report this at "
                + ISSUE_TRACKER;
    }

    /**
     * Turns a stand-in failure into a result. Only a {@link ParseException} means the input is at
     * fault; anything else reached us from a phase that runs on source that already parsed, and is
     * reported as a javafmt bug rather than as a problem with the user's code.
     */
    FormatResult failureToResult(final String source, final RuntimeException failure) {
        return failure instanceof ParseException parseError ? parseExceptionToResult(source, parseError) : internalErrorResult(source, failure);
    }

    FormatResult parseExceptionToResult(final String source, final ParseException e) {
        final var diagnostics = e.getDiagnostics();
        if (diagnostics.isEmpty()) {
            final var msg = e.getMessage();
            return new FormatResult(source, List.of(new Diagnostic.ParseError(msg == null ? e.toString() : msg, new Position.Unknown())));
        }
        return new FormatResult(source, diagnostics);
    }
}
