package io.github.jschneidereit.grind;

import io.github.jschneidereit.grind.ir.DocBuilder;
import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.printer.Printer;

public final class Grind {

    private static final int LINE_WIDTH = 150;

    private Grind() {}

    public static String format(final String source) {
        if (source.isEmpty()) {
            return source;
        }
        return Printer.print(DocBuilder.build(JavaParser.parse(source)), LINE_WIDTH);
    }
}
