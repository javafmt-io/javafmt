package io.github.jschneidereit.grind.printer;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

import io.github.jschneidereit.grind.doc.Doc;

public record GreedyPrintStrategy() implements PrintStrategy {

    static final GreedyPrintStrategy INSTANCE = new GreedyPrintStrategy();

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
                    final var mode = (frame.mode() == Mode.FLAT || fits(d, lineWidth - col))
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
                if (!forceBreakNextSep && fits(nextContent, lineWidth - currentCol - sepFlatWidth)) {
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

    // Group-fit predicate, short-circuiting the moment the running budget goes negative.
    // Each call is bounded by min(subtree size, remaining + 1), so total Group-decision work
    // across a print is O(n) under the project's line-width constant. HardLine is treated as
    // "doesn't fit flat" — it can never be rendered flat.
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

    // Returns the flat-render width of `doc`, or -1 if it contains a HardLine.
    // The -1 is a flag (not a width): in Fill content it tells the caller to force
    // the next separator to BREAK, since the line ends after the HardLine regardless
    // of where the rendered cursor lands. See
    // PrinterTest#fill_hardLineInsideContent_forcesNextSeparatorToBreak.
    private static int flatWidthOrBreak(final Doc doc) {
        var total = 0;
        final var work = new ArrayDeque<Doc>();
        work.push(doc);
        while (!work.isEmpty()) {
            switch (work.pop()) {
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
