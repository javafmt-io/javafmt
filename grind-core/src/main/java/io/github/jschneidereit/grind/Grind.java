package io.github.jschneidereit.grind;

import io.github.jschneidereit.grind.ir.DocBuilder;
import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.parser.ParseException;
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

    public static String formatStrict(final String source) {
        return doFormat(source, GrindConfig.defaults().withStrict(true));
    }

    public static FormatResult formatWithResult(final String source) {
        return formatWithResult(source, GrindConfig.defaults());
    }

    public static FormatResult formatWithResult(final String source, final GrindConfig config) {
        if (source.isEmpty()) {
            return new FormatResult(source, List.of());
        }
        try {
            return new FormatResult(doFormat(source, config), List.of());
        } catch (final ParseException e) {
            final var msg = e.getMessage();
            return new FormatResult(source, List.of(Diagnostic.parseError(msg == null ? e.toString() : msg)));
        }
    }

    private static String doFormat(final String source, final GrindConfig config) {
        return Printer.print(DocBuilder.build(JavaParser.parseUnit(source), config), LINE_WIDTH);
    }

    private Grind() {}
}
