package io.javafmt;

import io.javafmt.builder.DocBuilder;
import io.javafmt.lint.LintEngine;
import io.javafmt.lint.LintRules;
import io.javafmt.parser.ParseException;
import io.javafmt.parser.RoundTripVerifier;
import io.javafmt.printer.Printer;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class FormatStages {

    private final Printer PRINTER = new Printer(150);

    /** Where a refusal tells the user to send the file that triggered it. */
    private final String ISSUE_TRACKER = "https://github.com/javafmt-io/javafmt/issues";

    private final LintEngine LINT_ENGINE = new LintEngine(LintRules.defaults());

    private final LintEngine NO_RULES_ENGINE = new LintEngine(List.of());

    /**
     * Puts the input's BOM and line ending back on a result. Everything between the parser and the
     * printer works in LF with no BOM; writing that out verbatim would rewrite every line of a CRLF
     * file and bury the real change in a whole-file diff.
     */
    FormatResult asWritten(final SourceEncoding encoding, final FormatResult result) {
        return new FormatResult(encoding.restore(result.output()), result.diagnostics());
    }

    FormatResult withDiagnostics(final FormatResult result, final List<Diagnostic> diagnostics) {
        return diagnostics.isEmpty()
                ? result
                : new FormatResult(result.output(), Stream.concat(result.diagnostics().stream(), diagnostics.stream()).toList());
    }

    Diagnostic attributionWarning(final RuntimeException cause) {
        return new Diagnostic.Warning(
                "--analyze requested but attribution failed (" + cause.getClass().getSimpleName() + ": " + cause.getMessage()
                        + "); formatted without the rules that need resolved types",
                new Position.Unknown());
    }

    LintEngine lintEngine(final JavafmtConfig config) {
        return config.lint() ? LINT_ENGINE : NO_RULES_ENGINE;
    }

    /**
     * Builds and prints one already-linted unit, guarding the stage so a renderer bug comes back as
     * this file's diagnostic.
     */
    FormatResult render(final String source, final LintEngine.Outcome lintOutcome, final JavafmtConfig config) {
        try {
            final var built = DocBuilder.buildWithFallbacks(lintOutcome.unit(), config);
            final var output = withOneTrailingNewline(PRINTER.print(built.doc()));
            final var linted = lintOutcome.unit().source();
            final var mismatch = verifyOutput(config, source, linted, output);
            if (mismatch.isPresent()) {
                return new FormatResult(source, List.of(new Diagnostic.InternalError(mismatch.get(), whereItDiverged(linted, output))));
            }
            return withDiagnostics(new FormatResult(output, built.diagnostics()), lintOutcome.diagnostics());
        } catch (RuntimeException | AssertionError | StackOverflowError t) {
            return internalErrorResult(source, t);
        }
    }

    private String withOneTrailingNewline(final String printed) {
        final var end = printed.length()
                - (int) IntStream.iterate(printed.length() - 1, i -> i >= 0 && printed.charAt(i) == '\n', i -> i - 1).count();
        if (end == 0) {
            return "";
        }
        return end == printed.length() - 1 ? printed : printed.substring(0, end) + "\n";
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
                .or(() -> droppedComments(original, linted, output));
    }

    private Optional<String> droppedComments(final String original, final String linted, final String output) {
        if (original.equals(linted)) {
            return Optional.empty();
        }
        return RoundTripVerifier.verifyComments(original, output).map(m -> refusal("a lint rule dropped one of your comments", m));
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

    private FormatResult parseExceptionToResult(final String source, final ParseException e) {
        final var diagnostics = e.getDiagnostics();
        if (diagnostics.isEmpty()) {
            final var msg = e.getMessage();
            return new FormatResult(source, List.of(new Diagnostic.ParseError(msg == null ? e.toString() : msg, new Position.Unknown())));
        }
        return new FormatResult(source, diagnostics);
    }
}
