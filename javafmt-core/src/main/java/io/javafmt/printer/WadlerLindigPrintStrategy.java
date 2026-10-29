package io.javafmt.printer;

import io.javafmt.doc.Doc;
import java.util.ArrayDeque;

public record WadlerLindigPrintStrategy() implements PrintStrategy, PrintEngine.FitPolicy {

    static final WadlerLindigPrintStrategy INSTANCE = new WadlerLindigPrintStrategy();

    @Override
    public String print(final int lineWidth, final Doc doc) {
        return PrintEngine.print(lineWidth, doc, this);
    }

    @Override
    public boolean groupFits(final PrintEngine.Frame candidate, final int budget, final ArrayDeque<PrintEngine.Frame> upcoming) {
        return fitsForward(candidate, budget, upcoming);
    }

    @Override
    public boolean fillContentFits(final Doc content, final int remaining) {
        return fitsFlat(content, remaining);
    }

    /**
     * Wadler-Lindig forward fit-check. Walks the candidate frame followed by the upcoming
     * frames in stack-iteration order (no copy — this only iterates {@code upcoming}, never
     * mutates it), accumulating column width until either the budget goes negative (NOT FIT), a
     * forced line break is reached <em>past</em> the candidate (FIT — the current line ends), or
     * the stream ends (FIT). This bounds each call by {@code min(subtree size, upcoming size)}
     * rather than re-walking the whole remaining document, which is what keeps total fit-check
     * work O(n) across a print instead of O(n²). Inner Group choices are assumed FLAT (best case
     * for fit).
     *
     * <p><b>HardLine semantics.</b> Encountered while still walking the candidate's own
     * descendants, it means "this Group's contents include a forced break, so flat rendering is
     * impossible" — NOT FIT. Encountered after the candidate is fully consumed (in
     * {@code upcoming}), it means "the line ends here regardless of what we choose" — FIT. The
     * same distinction applies implicitly to Line/SoftLine in BREAK mode: those modes only
     * appear in upcoming frames (the candidate and all its descendants are pushed in FLAT mode),
     * so seeing a BREAK-mode Line/SoftLine necessarily means we are past the candidate.
     *
     * <p><b>Verbatim.</b> A literal's internal newlines are part of its content, not a layout
     * decision, so only the first line's width is budgeted — the surrounding Group is free to
     * render flat with the literal's body trailing inside.
     */
    private static boolean fitsForward(final PrintEngine.Frame candidate, final int budget, final ArrayDeque<PrintEngine.Frame> upcoming) {
        if (budget < 0) {
            return false;
        }
        var b = budget;
        final var work = new ArrayDeque<PrintEngine.Frame>();
        work.push(candidate);
        final var iter = upcoming.iterator();
        var consumedCandidate = false;
        while (true) {
            final PrintEngine.Frame f;
            if (!work.isEmpty()) {
                f = work.pop();
            } else if (iter.hasNext()) {
                f = iter.next();
                consumedCandidate = true;
            } else {
                return true;
            }
            switch (f.doc()) {
                case Doc.Text(var s) -> {
                    b -= s.length();
                    if (b < 0) {
                        return false;
                    }
                }
                case Doc.Verbatim(var s, var ignored) -> {
                    final var firstNewline = s.indexOf('\n');
                    b -= firstNewline >= 0 ? firstNewline : s.length();
                    if (b < 0) {
                        return false;
                    }
                }
                case Doc.Line() -> {
                    if (f.mode() == PrintEngine.Mode.FLAT) {
                        b -= 1;
                        if (b < 0) {
                            return false;
                        }
                    } else {
                        return true;
                    }
                }
                case Doc.SoftLine() -> {
                    if (f.mode() == PrintEngine.Mode.BREAK) {
                        return true;
                    }
                }
                case Doc.HardLine() -> {
                    return consumedCandidate;
                }
                case Doc.Indent(var d) -> work.push(new PrintEngine.Frame(f.indent() + PrintEngine.INDENT_SIZE, f.mode(), d));
                case Doc.Group(var d, var ignored) -> work.push(new PrintEngine.Frame(f.indent(), PrintEngine.Mode.FLAT, d));
                case Doc.IfBreak(var bc, var fc) ->
                    work.push(new PrintEngine.Frame(f.indent(), f.mode(), f.mode() == PrintEngine.Mode.BREAK ? bc : fc));
                case Doc.Concat(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        work.push(new PrintEngine.Frame(f.indent(), f.mode(), parts.get(i)));
                    }
                }
                case Doc.Fill(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        work.push(new PrintEngine.Frame(f.indent(), PrintEngine.Mode.FLAT, parts.get(i)));
                    }
                }
            }
        }
    }

    /**
     * Whether {@code doc} fits flat in {@code remaining} columns, for {@link Doc.Fill}'s "can the
     * next content element pack onto the line in progress" decision — a different question from
     * {@link #fitsForward}'s "may this Group render flat". A literal's own newlines are content
     * rather than a layout choice it could avoid, so where {@code fitsForward} budgets only a
     * multi-line {@link Doc.Verbatim}'s first line, here a multi-line Verbatim never fits: a
     * literal that ends the line in progress cannot be packed onto it.
     */
    private static boolean fitsFlat(final Doc doc, final int remaining) {
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
                    if (s.indexOf('\n') >= 0) {
                        return false;
                    }
                    budget -= s.length();
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
