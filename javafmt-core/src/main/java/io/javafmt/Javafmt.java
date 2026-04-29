package io.javafmt;

import io.javafmt.builder.DocBuilder;
import io.javafmt.lint.ArrayTrailingComma;
import io.javafmt.lint.DefaultComesLast;
import io.javafmt.lint.ExplodeStarImports;
import io.javafmt.lint.FallThrough;
import io.javafmt.lint.FinalLocalVariable;
import io.javafmt.lint.FinalParameters;
import io.javafmt.lint.LintEngine;
import io.javafmt.lint.LintRule;
import io.javafmt.lint.NeedBraces;
import io.javafmt.lint.RemoveUnusedImports;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParseException;
import io.javafmt.parser.ParseOutcome;
import io.javafmt.parser.ParsedUnit;
import io.javafmt.printer.PrintStrategy;
import io.javafmt.printer.Printer;

import java.util.List;
import java.util.Objects;

public final class Javafmt {

    private static final int LINE_WIDTH = 150;

    private static final List<LintRule> LINT_RULES = List.of(
        new FinalLocalVariable(),
        new FinalParameters(),
        new ArrayTrailingComma(),
        new ExplodeStarImports(),
        new RemoveUnusedImports(),
        new NeedBraces(),
        new FallThrough(),
        new DefaultComesLast());

    private static final LintEngine LINT_ENGINE = new LintEngine(LINT_RULES);

    public static String format(final String source, final JavafmtConfig config) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(config, "config");
        return formatWithResult(source, config).output();
    }

    public static String format(final String source) {
        Objects.requireNonNull(source, "source");
        return format(source, JavafmtConfig.defaults());
    }

    public static FormatResult formatWithResult(final String source) {
        Objects.requireNonNull(source, "source");
        return formatWithResult(source, JavafmtConfig.defaults());
    }

    public static FormatResult formatWithResult(final String source, final JavafmtConfig config) {
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
    public static FormatResult formatWithResult(final String source, final ParseOutcome outcome) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(outcome, "outcome");
        return formatWithResult(source, outcome, JavafmtConfig.defaults());
    }

    public static FormatResult formatWithResult(final String source, final ParseOutcome outcome, final JavafmtConfig config) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(config, "config");
        return switch (outcome) {
            case ParseOutcome.Ok ok -> formatParsed(source, ok.unit(), config);
            case ParseOutcome.Failed failed -> parseExceptionToResult(source, failed.error());
        };
    }

    static FormatResult formatWithResult(final String source, final JavafmtConfig config, final PrintStrategy strategy) {
        if (source.isEmpty()) {
            return new FormatResult(source, List.of());
        }
        final ParsedUnit unit;
        try {
            unit = JavaParser.parseUnit(source);
        } catch (final ParseException e) {
            return parseExceptionToResult(source, e);
        }
        return formatParsed(source, unit, config, strategy);
    }

    static FormatResult formatParsed(final String source, final ParsedUnit unit, final JavafmtConfig config) {
        return formatParsed(source, unit, config, PrintStrategy.wadlerLindig());
    }

    static FormatResult formatParsed(final String source, final ParsedUnit unit, final JavafmtConfig config, final PrintStrategy strategy) {
        try {
            final var lintOutcome = LINT_ENGINE.lint(unit);
            final var built = DocBuilder.buildWithFallbacks(lintOutcome.unit(), config);
            final var output = new Printer(LINE_WIDTH, strategy).print(built.doc());
            final var diagnostics = new java.util.ArrayList<Diagnostic>(built.diagnostics());
            diagnostics.addAll(lintOutcome.diagnostics());
            return new FormatResult(output, diagnostics);
        } catch (final RuntimeException | AssertionError t) {
            final var msg = t.getMessage();
            return new FormatResult(source, List.of(new Diagnostic.ParseError(
                t.getClass().getSimpleName() + ": " + (msg == null ? t.toString() : msg),
                Position.UNKNOWN)));
        }
    }

    private static FormatResult parseExceptionToResult(final String source, final ParseException e) {
        final var diagnostics = e.getDiagnostics();
        if (diagnostics.isEmpty()) {
            final var msg = e.getMessage();
            return new FormatResult(source, List.of(new Diagnostic.ParseError(
                msg == null ? e.toString() : msg, Position.UNKNOWN)));
        }
        return new FormatResult(source, diagnostics);
    }

    private Javafmt() {}
}
