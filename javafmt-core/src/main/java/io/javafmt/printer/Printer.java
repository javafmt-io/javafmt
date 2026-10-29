package io.javafmt.printer;

import io.javafmt.doc.Doc;

public record Printer(int lineWidth) {

    public Printer {
        if (lineWidth <= 0) {
            throw new IllegalArgumentException("lineWidth must be positive, got: " + lineWidth);
        }
    }

    public String print(final Doc doc) {
        return PrintEngine.print(lineWidth, doc);
    }
}
