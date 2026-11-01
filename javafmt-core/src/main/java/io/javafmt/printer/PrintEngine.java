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
        final var output = new StringBuilder();
        final var stack = new ArrayDeque<Frame>();
        final var work = new ArrayDeque<Frame>();
        stack.push(new Frame(0, Mode.BREAK, doc));
        var column = 0;
        var verbatimEnd = 0;
        while (!stack.isEmpty()) {
            final var frame = stack.pop();
            switch (frame.doc()) {
                case Doc.Text(var text) -> {
                    output.append(text);
                    column += text.length();
                }
                case Doc.Verbatim(var text, var sourceColumn) -> {
                    if (text.startsWith("\n")) {
                        stripTrailingWhitespace(output, verbatimEnd);
                    }
                    column = VerbatimEmitter.emit(output, column, text, sourceColumn);
                    verbatimEnd = text.isEmpty() ? verbatimEnd : output.length();
                }
                case Doc.BlockComment(var lines, var sourceColumn) ->
                    column = emitBlockComment(output, column, frame.indent(), lines, sourceColumn, verbatimEnd);
                case Doc.Line _ when frame.mode() == Mode.FLAT -> {
                    output.append(' ');
                    column++;
                }
                case Doc.SoftLine _ when frame.mode() == Mode.FLAT -> {}
                case Doc.Line _, Doc.SoftLine _, Doc.HardLine _ -> {
                    stripTrailingWhitespace(output, verbatimEnd);
                    output.append('\n').repeat(' ', frame.indent());
                    column = frame.indent();
                }
                case Doc.Indent(var contents) -> stack.push(new Frame(frame.indent() + INDENT_SIZE, frame.mode(), contents));
                case Doc.Align(var contents) -> stack.push(new Frame(column, frame.mode(), contents));
                case Doc.Choice(var preferred, var fallback, var fit) -> {
                    final var preferredFrame = new Frame(frame.indent(), frame.mode(), preferred);
                    final var fits = frame.mode() == Mode.FLAT || fits(preferredFrame, column, lineWidth, stack, work, reachOf(fit));
                    stack.push(fits ? preferredFrame : new Frame(frame.indent(), frame.mode(), fallback));
                }
                case Doc.Concat(var parts) -> {
                    for (var index = parts.size() - 1; index >= 0; index--) {
                        stack.push(new Frame(frame.indent(), frame.mode(), parts.get(index)));
                    }
                }
                case Doc.Fill(var parts) when frame.mode() == Mode.FLAT -> pushParts(stack, frame, parts);
                case Doc.Fill(var parts) -> fill(frame, parts, lineWidth - column, stack, work);
                case Doc.Group(var contents, var preferBreak) -> {
                    final var flat = new Frame(frame.indent(), Mode.FLAT, contents);
                    final var fits = frame.mode() == Mode.FLAT || (!preferBreak && fitsForward(flat, lineWidth - column, stack, work));
                    stack.push(fits ? flat : new Frame(frame.indent(), Mode.BREAK, contents));
                }
                case Doc.IfBreak(var breakContents, var flatContents) ->
                    stack.push(new Frame(frame.indent(), frame.mode(), frame.mode() == Mode.BREAK ? breakContents : flatContents));
            }
        }
        stripTrailingWhitespace(output, verbatimEnd);
        return output.toString();
    }

    private int emitBlockComment(final StringBuilder output,
                                 final int column,
                                 final int indent,
                                 final List<String> lines,
                                 final int sourceColumn,
                                 final int verbatimEnd) {
        final var continuation = lines.subList(1, lines.size());
        final var minimumLeading = continuation.stream()
                .filter(line -> !line.isBlank())
                .mapToInt(PrintEngine::leadingSpaces)
                .min()
                .orElse(sourceColumn);
        final var shift = minimumLeading >= sourceColumn ? column - sourceColumn : indent - minimumLeading;
        output.append(lines.getFirst());
        continuation.forEach(line -> {
            stripTrailingWhitespace(output, verbatimEnd);
            output.append('\n').repeat(' ', Math.max(0, leadingSpaces(line) + shift)).append(line, leadingSpaces(line), line.length());
        });
        return output.length() - output.lastIndexOf("\n") - 1;
    }

    private int leadingSpaces(final String line) {
        return (int) line.chars().takeWhile(character -> character == ' ').count();
    }

    private void stripTrailingWhitespace(final StringBuilder output, final int verbatimEnd) {
        final var trailing = (int) IntStream.iterate(
                output.length() - 1, index -> index >= 0 && isSpaceOrTab(output.charAt(index)), index -> index - 1)
                .count();
        final var start = output.length() - trailing;
        if (start >= verbatimEnd) {
            output.setLength(start);
        }
    }

    private boolean isSpaceOrTab(final char character) {
        return character == ' ' || character == '\t';
    }

    /**
     * Wadler-Lindig forward fit-check. Walks the candidate frame followed by the upcoming
     * frames in stack-iteration order (no copy — this only iterates {@code upcoming}, never
     * mutates it), accumulating column width until either the budget goes negative (NOT FIT), a
     * forced line break is reached <em>past</em> the candidate (FIT — the current line ends), or
     * the stream ends (FIT). This bounds each call by {@code min(subtree size, upcoming size)}
     * rather than re-walking the whole remaining document, which is what keeps total fit-check
     * work O(n) across a print instead of O(n²). Inner Group choices are assumed FLAT (best case
     * for fit), except a Group that prefers to break: one inside the candidate means NOT FIT.
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
        return fits(candidate, 0, budget, upcoming, work, Reach.FIRST_LINE);
    }

    private Reach reachOf(final Doc.Choice.Fit fit) {
        return switch (fit) {
            case EVERY_LINE -> Reach.EVERY_LINE;
            case OPENING_LINE -> Reach.OPENING_LINE;
        };
    }

    private boolean fits(final Frame candidate,
                         final int startColumn,
                         final int lineWidth,
                         final ArrayDeque<Frame> upcoming,
                         final ArrayDeque<Frame> work,
                         final Reach reach) {
        var column = startColumn;
        work.clear();
        work.push(candidate);
        final var iterator = upcoming.iterator();
        var consumedCandidate = false;
        while (column <= lineWidth) {
            final Frame frame;
            if (!work.isEmpty()) {
                frame = work.pop();
            } else if (iterator.hasNext()) {
                frame = iterator.next();
                consumedCandidate = true;
            } else {
                return true;
            }
            switch (frame.doc()) {
                case Doc.Text(var text) -> column += text.length();
                case Doc.Verbatim(var text, var _) -> {
                    final var firstNewline = text.indexOf('\n');
                    column += firstNewline >= 0 ? firstNewline : text.length();
                }
                case Doc.BlockComment(var lines, var _) -> {
                    column += lines.getFirst().length();
                    if (reach == Reach.FIRST_LINE && column <= lineWidth) {
                        return consumedCandidate;
                    }
                }
                case Doc.Line _ when frame.mode() == Mode.FLAT -> column++;
                case Doc.SoftLine _ when frame.mode() == Mode.FLAT -> {}
                case Doc.HardLine _ -> {
                    if (consumedCandidate || reach == Reach.OPENING_LINE) {
                        return true;
                    }
                    if (reach == Reach.FIRST_LINE) {
                        return false;
                    }
                    column = frame.indent();
                }
                case Doc.Line _, Doc.SoftLine _ -> {
                    if (consumedCandidate || reach == Reach.FIRST_LINE) {
                        return true;
                    }
                    if (reach == Reach.OPENING_LINE) {
                        return false;
                    }
                    column = frame.indent();
                }
                case Doc.Indent(var contents) -> work.push(new Frame(frame.indent() + INDENT_SIZE, frame.mode(), contents));
                case Doc.Align(var contents) -> work.push(new Frame(column, frame.mode(), contents));
                case Doc.Group(var contents, var preferBreak) -> {
                    if (preferBreak && !consumedCandidate && reach != Reach.EVERY_LINE) {
                        return false;
                    }
                    work.push(new Frame(frame.indent(), Mode.FLAT, contents));
                }
                case Doc.IfBreak(var breakContents, var flatContents) ->
                    work.push(new Frame(frame.indent(), frame.mode(), frame.mode() == Mode.BREAK ? breakContents : flatContents));
                case Doc.Choice(var preferred, var fallback, var _) ->
                    work.push(new Frame(frame.indent(), frame.mode(), frame.mode() == Mode.BREAK ? fallback : preferred));
                case Doc.Concat(var parts) -> {
                    for (var index = parts.size() - 1; index >= 0; index--) {
                        work.push(new Frame(frame.indent(), frame.mode(), parts.get(index)));
                    }
                }
                case Doc.Fill(var parts) -> pushParts(work, frame, parts);
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
                case Doc.Verbatim(var text, var _) -> {
                    if (text.indexOf('\n') >= 0) {
                        return false;
                    }
                }
                case Doc.Text _ -> {}
                case Doc.Group(var contents, var preferBreak) -> {
                    if (preferBreak) {
                        return false;
                    }
                    pending.push(new Pending(contents, true));
                }
                case Doc.Indent(var contents) -> pending.push(new Pending(contents, grouped));
                case Doc.Align(var contents) -> pending.push(new Pending(contents, grouped));
                case Doc.IfBreak(var breakContents, var flatContents) -> pending.push(new Pending(grouped ? flatContents : breakContents, grouped));
                case Doc.Choice(var preferred, var fallback, var _) -> {
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
        for (var index = parts.size() - 1; index >= frame.firstPart(); index--) {
            target.push(new Frame(frame.indent(), frame.mode(), parts.get(index)));
        }
    }

    private enum Mode { FLAT, BREAK }

    private enum Reach { FIRST_LINE, OPENING_LINE, EVERY_LINE }

    private record Pending(Doc doc, boolean grouped) {}

    private record Frame(int indent, Mode mode, Doc doc, int firstPart) {
        Frame(final int indent, final Mode mode, final Doc doc) {
            this(indent, mode, doc, 0);
        }
    }
}
