package io.github.jschneidereit.grind.printer;

import io.github.jschneidereit.grind.ir.Doc;

public sealed interface PrintStrategy permits GreedyPrintStrategy, WadlerLindigPrintStrategy {

    String print(int lineWidth, Doc doc);

    static PrintStrategy greedy() {
        return GreedyPrintStrategy.INSTANCE;
    }

    static PrintStrategy wadlerLindig() {
        return WadlerLindigPrintStrategy.INSTANCE;
    }
}
