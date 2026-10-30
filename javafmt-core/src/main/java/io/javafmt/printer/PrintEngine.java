package io.javafmt.printer;

import io.javafmt.doc.Doc;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * The stack-machine printer. Each Group renders flat when {@link #fitsForward} says the rest of
 * its line fits, otherwise it breaks.
 */
@UtilityClass
final class PrintEngine {

    final int INDENT_SIZE = 4;

    String print(final int lineWidth, final Doc doc) {
        Objects.requireNonNull(doc, "doc");
        final var sb = new StringBuilder();
        final var stack = new ArrayDeque<Frame>();
        final var work = new ArrayDeque<Frame>();
        stack.push(new Frame(0, Mode.BREAK, doc));
        var col = 0;
        var verbatimEnd = 0;
        while (!stack.isEmpty()) {
            final var frame = stack.pop();
            switch (frame.doc()) {
                case Doc.Text(var s) -> {
                    sb.append(s);
                    col += s.length();
                }
                case Doc.Verbatim(var s, var sourceCol) -> {
                    if (s.startsWith("\n")) {
                        stripTrailingWhitespace(sb, verbatimEnd);
                    }
                    col = VerbatimEmitter.emit(sb, col, s, sourceCol);
                    verbatimEnd = s.isEmpty() ? verbatimEnd : sb.length();
                }
                case Doc.BlockComment(var lines, var sourceCol) -> col = emitBlockComment(sb, col, frame.indent(), lines, sourceCol, verbatimEnd);
                case Doc.Line _ when frame.mode() == Mode.FLAT -> {
                    sb.append(' ');
                    col++;
                }
                case Doc.SoftLine _ when frame.mode() == Mode.FLAT -> {}
                case Doc.Line _, Doc.SoftLine _, Doc.HardLine _ -> {
                    stripTrailingWhitespace(sb, verbatimEnd);
                    sb.append('\n').repeat(' ', frame.indent());
                    col = frame.indent();
                }
                case Doc.Indent(var d) -> stack.push(new Frame(frame.indent() + INDENT_SIZE, frame.mode(), d));
                case Doc.Concat(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        stack.push(new Frame(frame.indent(), frame.mode(), parts.get(i)));
                    }
                }
                case Doc.Group(var d, var preferBreak) -> {
                    final var flat = new Frame(frame.indent(), Mode.FLAT, d);
                    final var fits = frame.mode() == Mode.FLAT || (!preferBreak && fitsForward(flat, lineWidth - col, stack, work));
                    stack.push(fits ? flat : new Frame(frame.indent(), Mode.BREAK, d));
                }
                case Doc.IfBreak(var breakContents, var flatContents) ->
                    stack.push(new Frame(frame.indent(), frame.mode(), frame.mode() == Mode.BREAK ? breakContents : flatContents));
            }
        }
        stripTrailingWhitespace(sb, verbatimEnd);
        return sb.toString();
    }

    private int emitBlockComment(
            final StringBuilder sb,
            final int col,
            final int indent,
            final List<String> lines,
            final int sourceCol,
            final int verbatimEnd) {
        final var continuation = lines.subList(1, lines.size());
        final var minLeading = continuation.stream().filter(line -> !line.isBlank()).mapToInt(PrintEngine::leadingSpaces).min().orElse(sourceCol);
        final var shift = minLeading >= sourceCol ? col - sourceCol : indent - minLeading;
        sb.append(lines.getFirst());
        continuation.forEach(line -> {
            stripTrailingWhitespace(sb, verbatimEnd);
            sb.append('\n').repeat(' ', Math.max(0, leadingSpaces(line) + shift)).append(line, leadingSpaces(line), line.length());
        });
        return sb.length() - sb.lastIndexOf("\n") - 1;
    }

    private int leadingSpaces(final String line) {
        return (int) line.chars().takeWhile(c -> c == ' ').count();
    }

    private void stripTrailingWhitespace(final StringBuilder sb, final int verbatimEnd) {
        final var start = sb.length() - (int) IntStream.iterate(sb.length() - 1, i -> i >= 0 && isSpaceOrTab(sb.charAt(i)), i -> i - 1).count();
        if (start >= verbatimEnd) {
            sb.setLength(start);
        }
    }

    private boolean isSpaceOrTab(final char c) {
        return c == ' ' || c == '\t';
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
    private boolean fitsForward(final Frame candidate, final int budget, final ArrayDeque<Frame> upcoming, final ArrayDeque<Frame> work) {
        var b = budget;
        work.clear();
        work.push(candidate);
        final var iter = upcoming.iterator();
        var consumedCandidate = false;
        while (b >= 0) {
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
                case Doc.Text(var s) -> b -= s.length();
                case Doc.Verbatim(var s, var ignored) -> {
                    final var firstNewline = s.indexOf('\n');
                    b -= firstNewline >= 0 ? firstNewline : s.length();
                }
                case Doc.Line _, Doc.SoftLine _ when f.mode() == Mode.BREAK -> {
                    return true;
                }
                case Doc.Line _ -> b--;
                case Doc.SoftLine _ -> {}
                case Doc.HardLine _ -> {
                    return consumedCandidate;
                }
                case Doc.BlockComment(var lines, var ignored) -> {
                    b -= lines.getFirst().length();
                    if (b >= 0) {
                        return consumedCandidate;
                    }
                }
                case Doc.Indent(var d) -> work.push(new Frame(f.indent() + INDENT_SIZE, f.mode(), d));
                case Doc.Group(var d, var ignored) -> work.push(new Frame(f.indent(), Mode.FLAT, d));
                case Doc.IfBreak(var bc, var fc) -> work.push(new Frame(f.indent(), f.mode(), f.mode() == Mode.BREAK ? bc : fc));
                case Doc.Concat(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        work.push(new Frame(f.indent(), f.mode(), parts.get(i)));
                    }
                }
            }
        }
        return false;
    }

    private enum Mode { FLAT, BREAK }

    private record Frame(int indent, Mode mode, Doc doc) {}
}
