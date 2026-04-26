package io.github.jschneidereit.grind.printer;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

import io.github.jschneidereit.grind.ir.Doc;

public record WadlerLindigPrintStrategy() implements PrintStrategy {

    static final WadlerLindigPrintStrategy INSTANCE = new WadlerLindigPrintStrategy();

    private static final int INDENT_SIZE = 4;

    private enum Mode { FLAT, BREAK }

    private record Frame(int indent, Mode mode, Doc doc) {}

    @Override
    public String print(final int lineWidth, final Doc doc) {
        Objects.requireNonNull(doc, "doc");

        final var sb = new StringBuilder();
        final var stack = new ArrayDeque<Frame>();
        stack.push(new Frame(0, Mode.BREAK, doc));
        var col = 0;

        while (!stack.isEmpty()) {
            final var frame = stack.pop();
            switch (frame.doc()) {
                case Doc.Text(var s) -> {
                    sb.append(s);
                    col += s.length();
                }
                case Doc.Line() -> {
                    if (frame.mode() == Mode.FLAT) {
                        sb.append(' ');
                        col += 1;
                    } else {
                        sb.append('\n').repeat(' ', frame.indent());
                        col = frame.indent();
                    }
                }
                case Doc.SoftLine() -> {
                    if (frame.mode() == Mode.BREAK) {
                        sb.append('\n').repeat(' ', frame.indent());
                        col = frame.indent();
                    }
                }
                case Doc.HardLine() -> {
                    sb.append('\n').repeat(' ', frame.indent());
                    col = frame.indent();
                }
                case Doc.Indent(var d) ->
                    stack.push(new Frame(frame.indent() + INDENT_SIZE, frame.mode(), d));
                case Doc.Concat(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        stack.push(new Frame(frame.indent(), frame.mode(), parts.get(i)));
                    }
                }
                case Doc.Group(var d) -> {
                    final var candidate = new Frame(frame.indent(), Mode.FLAT, d);
                    final var mode = (frame.mode() == Mode.FLAT
                        || fitsForward(candidate, lineWidth - col, stack))
                        ? Mode.FLAT : Mode.BREAK;
                    stack.push(new Frame(frame.indent(), mode, d));
                }
                case Doc.IfBreak(var breakContents, var flatContents) ->
                    stack.push(new Frame(frame.indent(), frame.mode(),
                        frame.mode() == Mode.BREAK ? breakContents : flatContents));
                case Doc.Fill(var parts) -> {
                    final var newFrames = expandFill(parts, frame.indent(), col, lineWidth);
                    for (var i = newFrames.size() - 1; i >= 0; i--) {
                        stack.push(newFrames.get(i));
                    }
                }
            }
        }

        return sb.toString();
    }

    // Wadler-Lindig forward fit-check. Walks the candidate frame followed by the upcoming
    // frames in stack-iteration order (no copy — we only iterate `upcoming`, never mutate it),
    // accumulating column width until either the budget goes negative (NOT FIT), a forced
    // line break is reached *past* the candidate (FIT — current line ends), or the stream
    // ends (FIT). Inner Group choices are assumed FLAT (best case for fit).
    //
    // HardLine semantics: encountered while still walking the candidate's own descents,
    // it means "this Group's contents include a forced break, so flat rendering is impossible"
    // → NOT FIT. Encountered after the candidate is fully consumed (in upcoming), it means
    // "the line ends here regardless of what we choose" → FIT. The same distinction applies
    // implicitly to Line/SoftLine in BREAK mode: those modes only appear in upcoming frames
    // (the candidate and all its descents are pushed in FLAT mode), so seeing a BREAK-mode
    // Line/SoftLine necessarily means we are past the candidate.
    private static boolean fitsForward(final Frame candidate, final int budget, final ArrayDeque<Frame> upcoming) {
        if (budget < 0) {
            return false;
        }
        var b = budget;
        final var work = new ArrayDeque<Frame>();
        work.push(candidate);
        final var iter = upcoming.iterator();
        var consumedCandidate = false;

        while (true) {
            final Frame f;
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
                case Doc.Line() -> {
                    if (f.mode() == Mode.FLAT) {
                        b -= 1;
                        if (b < 0) {
                            return false;
                        }
                    } else {
                        return true;
                    }
                }
                case Doc.SoftLine() -> {
                    if (f.mode() == Mode.BREAK) {
                        return true;
                    }
                }
                case Doc.HardLine() -> { return consumedCandidate; }
                case Doc.Indent(var d) ->
                    work.push(new Frame(f.indent() + INDENT_SIZE, f.mode(), d));
                case Doc.Group(var d) ->
                    work.push(new Frame(f.indent(), Mode.FLAT, d));
                case Doc.IfBreak(var bc, var fc) ->
                    work.push(new Frame(f.indent(), f.mode(),
                        f.mode() == Mode.BREAK ? bc : fc));
                case Doc.Concat(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        work.push(new Frame(f.indent(), f.mode(), parts.get(i)));
                    }
                }
                case Doc.Fill(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        work.push(new Frame(f.indent(), Mode.FLAT, parts.get(i)));
                    }
                }
            }
        }
    }

    // Wadler-Lindig has no native Fill combinator. The greedy per-separator pack from
    // GreedyPrintStrategy is preserved here verbatim. Reformulating Fill as nested Groups
    // is the principled WL alternative but would change every Fill callsite's output and
    // is out of scope for this strategy.
    private static List<Frame> expandFill(final List<Doc> parts, final int indent, final int col, final int lineWidth) {
        final var out = new java.util.ArrayList<Frame>();
        var currentCol = col;
        var forceBreakNextSep = false;
        for (var i = 0; i < parts.size(); i++) {
            final var part = parts.get(i);
            final var isSep = (i & 1) == 1;
            if (!isSep) {
                out.add(new Frame(indent, Mode.FLAT, part));
                final var w = flatWidthOrBreak(part);
                if (w < 0) {
                    forceBreakNextSep = true;
                } else {
                    currentCol += w;
                }
            } else {
                final var nextContent = parts.get(i + 1);
                final var sepFlatWidth = part instanceof Doc.Line ? 1 : 0;
                if (!forceBreakNextSep && fitsFlat(nextContent, lineWidth - currentCol - sepFlatWidth)) {
                    out.add(new Frame(indent, Mode.FLAT, part));
                    currentCol += sepFlatWidth;
                } else {
                    out.add(new Frame(indent, Mode.BREAK, part));
                    currentCol = indent;
                    forceBreakNextSep = false;
                }
            }
        }
        return out;
    }

    private static boolean fitsFlat(final Doc doc, final int remaining) {
        if (remaining < 0) {
            return false;
        }
        var budget = remaining;
        final var work = new ArrayDeque<Doc>();
        work.push(doc);
        while (!work.isEmpty()) {
            final var d = work.pop();
            switch (d) {
                case Doc.Text(var s) -> {
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
                case Doc.SoftLine() -> { /* 0 in flat */ }
                case Doc.HardLine() -> { return false; }
                case Doc.Indent(var inner) -> work.push(inner);
                case Doc.Group(var inner) -> work.push(inner);
                case Doc.IfBreak ifBreak -> work.push(ifBreak.flatContents());
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

    // See GreedyPrintStrategy#flatWidthOrBreak.
    private static int flatWidthOrBreak(final Doc doc) {
        var total = 0;
        final var work = new ArrayDeque<Doc>();
        work.push(doc);
        while (!work.isEmpty()) {
            final var d = work.pop();
            switch (d) {
                case Doc.Text(var s) -> total += s.length();
                case Doc.Line() -> total += 1;
                case Doc.SoftLine() -> { /* 0 in flat */ }
                case Doc.HardLine() -> { return -1; }
                case Doc.Indent(var inner) -> work.push(inner);
                case Doc.Group(var inner) -> work.push(inner);
                case Doc.IfBreak ifBreak -> work.push(ifBreak.flatContents());
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
        return total;
    }
}
