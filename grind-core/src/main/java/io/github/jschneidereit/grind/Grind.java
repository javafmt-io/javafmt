package io.github.jschneidereit.grind;

import io.github.jschneidereit.grind.ir.DocBuilder;
import io.github.jschneidereit.grind.lint.ArrayTrailingComma;
import io.github.jschneidereit.grind.lint.FinalLocalVariable;
import io.github.jschneidereit.grind.lint.FinalParameters;
import io.github.jschneidereit.grind.lint.LintEngine;
import io.github.jschneidereit.grind.lint.LintRule;
import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.parser.ParseException;
import io.github.jschneidereit.grind.parser.ParseOutcome;
import io.github.jschneidereit.grind.parser.ParsedUnit;
import io.github.jschneidereit.grind.printer.PrintStrategy;
import io.github.jschneidereit.grind.printer.Printer;

import java.util.List;

public final class Grind {

    private static final int LINE_WIDTH = 150;

    private static final List<LintRule> LINT_RULES = List.of(
        new FinalLocalVariable(),
        new FinalParameters(),
        new ArrayTrailingComma());

    private static final LintEngine LINT_ENGINE = new LintEngine(LINT_RULES);

    public static String format(final String source, final GrindConfig config) {
        return formatWithResult(source, config).output();
    }

    public static String format(final String source) {
        return format(source, GrindConfig.defaults());
    }

    public static FormatResult formatWithResult(final String source) {
        return formatWithResult(source, GrindConfig.defaults());
    }

    public static FormatResult formatWithResult(final String source, final GrindConfig config) {
        return formatWithResult(source, config, PrintStrategy.wadlerLindig());
    }

    /**
     * Formats {@code source} using a pre-computed parse {@code outcome}, intended for callers
     * that batch-parsed via {@link JavaParser#parseUnits(List)} and want to amortize the
     * per-task setup across many files. Failed outcomes round-trip the source unchanged with
     * the parse diagnostics attached.
     */
    public static FormatResult formatWithResult(final String source, final ParseOutcome outcome) {
        return formatWithResult(source, outcome, GrindConfig.defaults());
    }

    public static FormatResult formatWithResult(final String source, final ParseOutcome outcome, final GrindConfig config) {
        return switch (outcome) {
            case ParseOutcome.Ok ok -> formatParsed(source, ok.unit(), config);
            case ParseOutcome.Failed failed -> parseExceptionToResult(source, failed.error());
        };
    }

    static FormatResult formatWithResult(final String source, final GrindConfig config, final PrintStrategy strategy) {
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

    static FormatResult formatParsed(final String source, final ParsedUnit unit, final GrindConfig config) {
        return formatParsed(source, unit, config, PrintStrategy.wadlerLindig());
    }

    static FormatResult formatParsed(final String source, final ParsedUnit unit, final GrindConfig config, final PrintStrategy strategy) {
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

    private Grind() {}
}
