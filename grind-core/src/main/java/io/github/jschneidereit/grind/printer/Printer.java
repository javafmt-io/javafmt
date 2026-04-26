package io.github.jschneidereit.grind.printer;

import java.util.Objects;

import io.github.jschneidereit.grind.ir.Doc;

public record Printer(int lineWidth, PrintStrategy strategy) {

    public Printer {
        Objects.requireNonNull(strategy, "strategy");
    }

    public Printer(final int lineWidth) {
        this(lineWidth, PrintStrategy.greedy());
    }

    public String print(final Doc doc) {
        return strategy.print(lineWidth, doc);
    }
}
