package io.javafmt.printer;

import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

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
                case Doc.Align(var d) -> stack.push(new Frame(col, frame.mode(), d));
                case Doc.Choice(var preferred, var fallback) -> {
                    final var preferredFrame = new Frame(frame.indent(), frame.mode(), preferred);
                    final var fits = frame.mode() == Mode.FLAT || fitsEveryLine(preferredFrame, col, lineWidth, stack, work);
                    stack.push(fits ? preferredFrame : new Frame(frame.indent(), frame.mode(), fallback));
                }
                case Doc.Concat(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        stack.push(new Frame(frame.indent(), frame.mode(), parts.get(i)));
                    }
                }
                case Doc.Fill(var parts) when frame.mode() == Mode.FLAT -> pushParts(stack, frame, parts);
                case Doc.Fill(var parts) -> fill(frame, parts, lineWidth - col, stack, work);
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

    private int emitBlockComment(final StringBuilder sb,
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
                case Doc.Verbatim(var s, var _) -> {
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
                case Doc.BlockComment(var lines, var _) -> {
                    b -= lines.getFirst().length();
                    if (b >= 0) {
                        return consumedCandidate;
                    }
                }
                case Doc.Indent(var d) -> work.push(new Frame(f.indent() + INDENT_SIZE, f.mode(), d));
                case Doc.Align(var d) -> work.push(new Frame(f.indent(), f.mode(), d));
                case Doc.Group(var d, var _) -> work.push(new Frame(f.indent(), Mode.FLAT, d));
                case Doc.IfBreak(var bc, var fc) -> work.push(new Frame(f.indent(), f.mode(), f.mode() == Mode.BREAK ? bc : fc));
                case Doc.Choice(var preferred, var fallback) ->
                    work.push(new Frame(f.indent(), f.mode(), f.mode() == Mode.BREAK ? fallback : preferred));
                case Doc.Concat(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        work.push(new Frame(f.indent(), f.mode(), parts.get(i)));
                    }
                }
                case Doc.Fill(var parts) -> pushParts(work, f, parts);
            }
        }
        return false;
    }

    private boolean fitsEveryLine(final Frame candidate,
                                  final int startColumn,
                                  final int lineWidth,
                                  final ArrayDeque<Frame> upcoming,
                                  final ArrayDeque<Frame> work) {
        var column = startColumn;
        work.clear();
        work.push(candidate);
        final var iterator = upcoming.iterator();
        var consumedCandidate = false;
        while (column <= lineWidth) {
            final Frame f;
            if (!work.isEmpty()) {
                f = work.pop();
            } else if (iterator.hasNext()) {
                f = iterator.next();
                consumedCandidate = true;
            } else {
                return true;
            }
            switch (f.doc()) {
                case Doc.Text(var s) -> column += s.length();
                case Doc.Verbatim(var s, var _) -> {
                    final var firstNewline = s.indexOf('\n');
                    column += firstNewline >= 0 ? firstNewline : s.length();
                }
                case Doc.BlockComment(var lines, var _) -> column += lines.getFirst().length();
                case Doc.Line _ when f.mode() == Mode.FLAT -> column++;
                case Doc.SoftLine _ when f.mode() == Mode.FLAT -> {}
                case Doc.Line _, Doc.SoftLine _, Doc.HardLine _ -> {
                    if (consumedCandidate) {
                        return true;
                    }
                    column = f.indent();
                }
                case Doc.Indent(var d) -> work.push(new Frame(f.indent() + INDENT_SIZE, f.mode(), d));
                case Doc.Align(var d) -> work.push(new Frame(column, f.mode(), d));
                case Doc.Group(var d, var _) -> work.push(new Frame(f.indent(), Mode.FLAT, d));
                case Doc.IfBreak(var bc, var fc) -> work.push(new Frame(f.indent(), f.mode(), f.mode() == Mode.BREAK ? bc : fc));
                case Doc.Choice(var preferred, var fallback) ->
                    work.push(new Frame(f.indent(), f.mode(), f.mode() == Mode.BREAK ? fallback : preferred));
                case Doc.Concat(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        work.push(new Frame(f.indent(), f.mode(), parts.get(i)));
                    }
                }
                case Doc.Fill(var parts) -> pushParts(work, f, parts);
            }
        }
        return false;
    }

    private void fill(final Frame frame, final List<Doc> parts, final int budget, final ArrayDeque<Frame> stack, final ArrayDeque<Frame> work) {
        final var first = frame.firstPart();
        if (first >= parts.size()) {
            return;
        }
        final var content = parts.get(first);
        final var contentFrame = new Frame(frame.indent(), Mode.BREAK, content);
        if (first + 1 == parts.size()) {
            stack.push(contentFrame);
            return;
        }
        final var separator = parts.get(first + 1);
        final var next = parts.get(first + 2);
        final var pair = new Frame(frame.indent(), Mode.FLAT, Doc.concat(content, separator, next));
        final var pairFits = fitsForward(pair, budget, following(frame, parts, first + 3, stack), work)
            && staysOnOneLine(content)
            && staysOnOneLine(next);
        stack.push(new Frame(frame.indent(), Mode.BREAK, frame.doc(), first + 2));
        stack.push(new Frame(frame.indent(), pairFits ? Mode.FLAT : Mode.BREAK, separator));
        stack.push(contentFrame);
    }

    private boolean staysOnOneLine(final Doc doc) {
        final var pending = new ArrayDeque<Pending>();
        pending.push(new Pending(doc, false));
        while (!pending.isEmpty()) {
            final var current = pending.pop();
            final var grouped = current.grouped();
            switch (current.doc()) {
                case Doc.HardLine _, Doc.BlockComment _ -> {
                    return false;
                }
                case Doc.Line _, Doc.SoftLine _ -> {
                    if (!grouped) {
                        return false;
                    }
                }
                case Doc.Verbatim(var s, var _) -> {
                    if (s.indexOf('\n') >= 0) {
                        return false;
                    }
                }
                case Doc.Text _ -> {}
                case Doc.Group(var d, var preferBreak) -> {
                    if (preferBreak && !grouped) {
                        return false;
                    }
                    pending.push(new Pending(d, true));
                }
                case Doc.Indent(var d) -> pending.push(new Pending(d, grouped));
                case Doc.Align(var d) -> pending.push(new Pending(d, grouped));
                case Doc.IfBreak(var breakContents, var flatContents) -> pending.push(new Pending(grouped ? flatContents : breakContents, grouped));
                case Doc.Choice(var preferred, var fallback) -> {
                    pending.push(new Pending(preferred, grouped));
                    pending.push(new Pending(fallback, grouped));
                }
                case Doc.Concat(var parts) -> parts.forEach(part -> pending.push(new Pending(part, grouped)));
                case Doc.Fill(var parts) -> parts.forEach(part -> pending.push(new Pending(part, grouped)));
            }
        }
        return true;
    }

    private ArrayDeque<Frame> following(final Frame frame, final List<Doc> parts, final int index, final ArrayDeque<Frame> stack) {
        if (index >= parts.size()) {
            return stack;
        }
        final var next = new ArrayDeque<Frame>();
        next.push(new Frame(frame.indent(), Mode.BREAK, parts.get(index)));
        return next;
    }

    private void pushParts(final ArrayDeque<Frame> target, final Frame frame, final List<Doc> parts) {
        for (var i = parts.size() - 1; i >= frame.firstPart(); i--) {
            target.push(new Frame(frame.indent(), frame.mode(), parts.get(i)));
        }
    }

    private enum Mode { FLAT, BREAK }

    private record Pending(Doc doc, boolean grouped) {}

    private record Frame(int indent, Mode mode, Doc doc, int firstPart) {
        Frame(final int indent, final Mode mode, final Doc doc) {
            this(indent, mode, doc, 0);
        }
    }
}
