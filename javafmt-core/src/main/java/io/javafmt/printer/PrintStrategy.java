package io.javafmt.printer;

import io.javafmt.doc.Doc;

public sealed interface PrintStrategy permits GreedyPrintStrategy, WadlerLindigPrintStrategy {

    String print(int lineWidth, Doc doc);

    static PrintStrategy greedy() {
        return GreedyPrintStrategy.INSTANCE;
    }

    static PrintStrategy wadlerLindig() {
        return WadlerLindigPrintStrategy.INSTANCE;
    }
}
