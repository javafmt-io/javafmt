package io.javafmt.builder;

import io.javafmt.Javafmt;
import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;

final class RendererTestSupport {

    private static final int WIDTH = 150;

    static String format(final String source) {
        return new Printer(WIDTH).print(
                        DocBuilder.build(JavaParser.parseUnit(source)).doc())
                .stripTrailing();
    }

    static String formatWithJavafmt(final String source) {
        return Javafmt.format(source).stripTrailing();
    }

    private RendererTestSupport() {}
}
