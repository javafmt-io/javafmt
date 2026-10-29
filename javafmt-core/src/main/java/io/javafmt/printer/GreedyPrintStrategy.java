package io.javafmt.printer;

import io.javafmt.doc.Doc;
import java.util.ArrayDeque;

public record GreedyPrintStrategy() implements PrintStrategy, PrintEngine.FitPolicy {

    static final GreedyPrintStrategy INSTANCE = new GreedyPrintStrategy();

    @Override
    public String print(final int lineWidth, final Doc doc) {
        return PrintEngine.print(lineWidth, doc, this);
    }

    @Override
    public boolean groupFits(final PrintEngine.Frame candidate, final int budget, final ArrayDeque<PrintEngine.Frame> upcoming) {
        return fits(candidate.doc(), budget);
    }

    @Override
    public boolean fillContentFits(final Doc content, final int remaining) {
        return fits(content, remaining);
    }

    /**
     * Group-fit predicate, short-circuiting the moment the running budget goes negative. Each
     * call is bounded by {@code min(subtree size, remaining + 1)}, so total Group-decision work
     * across a print is O(n) under the project's line-width constant. {@link Doc.HardLine} is
     * treated as "doesn't fit flat" — it can never be rendered flat.
     */
    private static boolean fits(final Doc doc, final int remaining) {
        if (remaining < 0) {
            return false;
        }
        var budget = remaining;
        final var work = new ArrayDeque<Doc>();
        work.push(doc);
        while (!work.isEmpty()) {
            switch (work.pop()) {
                case Doc.Text(var s) -> {
                    budget -= s.length();
                    if (budget < 0) {
                        return false;
                    }
                }
                case Doc.Verbatim(var s, var ignored) -> {
                    final var firstNewline = s.indexOf('\n');
                    budget -= firstNewline >= 0 ? firstNewline : s.length();
                    if (budget < 0) {
                        return false;
                    }
                }
                case Doc.Line() -> {
                    budget -= 1;
                    if (budget < 0) {
                        return false;
                    }
                }
                case Doc.SoftLine() -> {}
                case Doc.HardLine() -> {
                    return false;
                }
                case Doc.Indent(var inner) -> work.push(inner);
                case Doc.Group(var inner, var ignored) -> work.push(inner);
                case Doc.IfBreak(var ignoredBreak, var flatContents) -> work.push(flatContents);
                case Doc.Concat(var ps) -> {
                    for (var i = ps.size() - 1; i >= 0; i--) {
                        work.push(ps.get(i));
                    }
                }
                case Doc.Fill(var ps) -> {
                    for (var i = ps.size() - 1; i >= 0; i--) {
                        work.push(ps.get(i));
                    }
                }
            }
        }
        return true;
    }
}
