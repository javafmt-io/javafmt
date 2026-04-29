package io.github.jschneidereit.grind.printer;

import java.util.Objects;

import io.github.jschneidereit.grind.doc.Doc;

public record Printer(int lineWidth, PrintStrategy strategy) {

    public Printer {
        Objects.requireNonNull(strategy, "strategy");
        if (lineWidth <= 0) {
            throw new IllegalArgumentException("lineWidth must be positive, got: " + lineWidth);
        }
    }

    public Printer(final int lineWidth) {
        this(lineWidth, PrintStrategy.greedy());
    }

    public String print(final Doc doc) {
        return strategy.print(lineWidth, doc);
    }
}
