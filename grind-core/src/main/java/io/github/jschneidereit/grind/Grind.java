package io.github.jschneidereit.grind;

import io.github.jschneidereit.grind.ir.DocBuilder;
import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.parser.ParseException;
import io.github.jschneidereit.grind.parser.ParsedUnit;
import io.github.jschneidereit.grind.printer.Printer;

import java.util.List;

public final class Grind {

    private static final int LINE_WIDTH = 150;

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
        if (source.isEmpty()) {
            return new FormatResult(source, List.of());
        }
        final ParsedUnit unit;
        try {
            unit = JavaParser.parseUnit(source);
        } catch (final ParseException e) {
            return parseExceptionToResult(source, e);
        }
        return formatParsed(source, unit, config);
    }

    static FormatResult formatParsed(final String source, final ParsedUnit unit, final GrindConfig config) {
        try {
            final var built = DocBuilder.buildWithFallbacks(unit, config);
            return new FormatResult(new Printer(LINE_WIDTH).print(built.doc()), built.diagnostics());
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
