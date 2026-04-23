package io.github.jschneidereit.grind.printer;

import java.util.ArrayDeque;
import java.util.Objects;

import io.github.jschneidereit.grind.ir.Doc;

public final class Printer {

    private static final int INDENT_SIZE = 4;

    private enum Mode { FLAT, BREAK }

    private record Frame(int indent, Mode mode, Doc doc) {}

    public static String print(final Doc doc, final int lineWidth) {
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
                        sb.append('\n');
                        sb.append(" ".repeat(frame.indent()));
                        col = frame.indent();
                    }
                }
                case Doc.SoftLine() -> {
                    if (frame.mode() == Mode.BREAK) {
                        sb.append('\n');
                        sb.append(" ".repeat(frame.indent()));
                        col = frame.indent();
                    }
                }
                case Doc.HardLine() -> {
                    sb.append('\n');
                    sb.append(" ".repeat(frame.indent()));
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
                    final var mode = (frame.mode() == Mode.FLAT || fits(lineWidth - col, d, frame.indent()))
                        ? Mode.FLAT : Mode.BREAK;
                    stack.push(new Frame(frame.indent(), mode, d));
                }
                case Doc.IfBreak(var breakContents, var flatContents) ->
                    stack.push(new Frame(frame.indent(), frame.mode(),
                        frame.mode() == Mode.BREAK ? breakContents : flatContents));
                case Doc.Fill(var parts) -> {
                    final var newFrames = expandFill(parts, frame.indent(), lineWidth, col);
                    for (var i = newFrames.size() - 1; i >= 0; i--) {
                        stack.push(newFrames.get(i));
                    }
                }
            }
        }

        return sb.toString();
    }

    private static java.util.List<Frame> expandFill(final java.util.List<Doc> parts, final int indent, final int lineWidth, final int col) {
        final var out = new java.util.ArrayList<Frame>();
        var currentCol = col;
        for (var i = 0; i < parts.size(); i++) {
            final var part = parts.get(i);
            final var isSep = (i & 1) == 1;
            if (!isSep) {
                out.add(new Frame(indent, Mode.FLAT, part));
                currentCol += flatWidth(part);
            } else {
                final var nextContent = parts.get(i + 1);
                final var sepFlatWidth = part instanceof Doc.Line ? 1 : 0;
                if (fits(lineWidth - currentCol - sepFlatWidth, nextContent, indent)) {
                    out.add(new Frame(indent, Mode.FLAT, part));
                    currentCol += sepFlatWidth;
                } else {
                    out.add(new Frame(indent, Mode.BREAK, part));
                    currentCol = indent;
                }
            }
        }
        return out;
    }

    private static int flatWidth(final Doc doc) {
        var total = 0;
        final var work = new ArrayDeque<Doc>();
        work.push(doc);
        while (!work.isEmpty()) {
            final var d = work.pop();
            switch (d) {
                case Doc.Text(var s) -> total += s.length();
                case Doc.Line() -> total += 1;
                case Doc.SoftLine() -> { /* 0 in flat */ }
                case Doc.HardLine() -> { return Integer.MAX_VALUE / 2; }
                case Doc.Indent(var inner) -> work.push(inner);
                case Doc.Group(var inner) -> work.push(inner);
                case Doc.IfBreak(var br, var flat) -> work.push(flat);
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

    /**
     * Returns true if {@code doc} rendered flat fits within {@code remaining} characters.
     * A {@link Doc.HardLine} always returns false — it forces the enclosing group to break.
     *
     * <p>Only {@code doc}'s own content is measured, not sibling docs that follow it on the
     * same line. A group goes flat whenever its content fits, even if subsequent siblings
     * would cause the line to overflow. This is intentional: each group decides independently.
     */
    private static boolean fits(final int remaining, final Doc doc, final int indent) {
        final var work = new ArrayDeque<Frame>();
        work.push(new Frame(indent, Mode.FLAT, doc));
        var rem = remaining;

        while (rem >= 0 && !work.isEmpty()) {
            final var frame = work.pop();
            switch (frame.doc()) {
                case Doc.Text(var s) -> rem -= s.length();
                case Doc.Line() -> rem -= 1;
                case Doc.SoftLine() -> { /* flat → nothing */ }
                case Doc.HardLine() -> { return false; }
                case Doc.Indent(var d) ->
                    work.push(new Frame(frame.indent() + INDENT_SIZE, Mode.FLAT, d));
                case Doc.Concat(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        work.push(new Frame(frame.indent(), Mode.FLAT, parts.get(i)));
                    }
                }
                case Doc.Group(var d) ->
                    work.push(new Frame(frame.indent(), Mode.FLAT, d));
                case Doc.IfBreak ifBreak ->
                    work.push(new Frame(frame.indent(), Mode.FLAT, ifBreak.flatContents()));
                case Doc.Fill(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        work.push(new Frame(frame.indent(), Mode.FLAT, parts.get(i)));
                    }
                }
            }
        }

        return rem >= 0;
    }

    private Printer() {}
}
