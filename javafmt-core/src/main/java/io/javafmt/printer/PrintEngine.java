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

    private final int CHAIN_COLUMN = 80;

    String print(final int lineWidth, final Doc doc) {
        Objects.requireNonNull(doc, "doc");
        final var output = new StringBuilder();
        final var stack = new ArrayDeque<Frame>();
        final var work = new ArrayDeque<Frame>();
        stack.push(new Frame(0, Mode.BREAK, doc));
        var column = 0;
        var verbatimEnd = 0;
        var lineCommentOpen = false;
        var blockCommentOpen = false;
        while (!stack.isEmpty()) {
            final var frame = stack.pop();
            if ((lineCommentOpen && isContent(frame.doc())) || (blockCommentOpen && isCode(frame.doc()))) {
                column = newline(output, verbatimEnd, Math.max(frame.indent(), leadingSpaces(currentLine(output)) + 2 * INDENT_SIZE));
                lineCommentOpen = false;
                blockCommentOpen = false;
            }
            switch (frame.doc()) {
                case Doc.Text(var text) -> {
                    output.append(text);
                    column += text.length();
                    lineCommentOpen = lineCommentOpen || startsALineComment(text);
                    blockCommentOpen = blockCommentOpen || startsABlockComment(text);
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
                case Doc.LineStart _ -> {
                    if (!currentLine(output).isBlank()) {
                        column = newline(output, verbatimEnd, frame.indent());
                    }
                    lineCommentOpen = false;
                    blockCommentOpen = false;
                }
                case Doc.Line _, Doc.SoftLine _, Doc.HardLine _ -> {
                    column = newline(output, verbatimEnd, frame.indent());
                    lineCommentOpen = false;
                    blockCommentOpen = false;
                }
                case Doc.Indent(var contents) -> stack.push(new Frame(frame.indent() + INDENT_SIZE, frame.mode(), contents));
                case Doc.Align(var contents) -> stack.push(new Frame(column, frame.mode(), contents));
                case Doc.Choice(var preferred, var fallback, var _, var chainWidth) when chainWidth >= 0 -> {
                    if (column + chainWidth <= CHAIN_COLUMN) {
                        stack.push(new Frame(frame.indent(), frame.mode(), preferred));
                    } else {
                        final var indent = frame.mode() == Mode.FLAT ? leadingSpaces(currentLine(output)) : frame.indent();
                        stack.push(new Frame(indent, Mode.BREAK, fallback));
                    }
                }
                case Doc.Choice(var preferred, var fallback, var fit, var _) -> {
                    final var preferredFrame = new Frame(frame.indent(), frame.mode(), preferred);
                    final var fallbackFrame = new Frame(frame.indent(), frame.mode(), fallback);
                    final var fits = frame.mode() == Mode.FLAT
                        || fits(preferredFrame, column, lineWidth, stack, work, reachOf(fit))
                        || (stopsAtTheFirstBreak(reachOf(fit)) && !fits(fallbackFrame, column, lineWidth, stack, work, Reach.NEXT_LINE_FIRST_BREAK));
                    stack.push(fits ? preferredFrame : fallbackFrame);
                }
                case Doc.Concat(var parts) -> {
                    for (var index = parts.size() - 1; index >= 0; index--) {
                        stack.push(new Frame(frame.indent(), frame.mode(), parts.get(index)));
                    }
                }
                case Doc.Group(var contents, var preferBreak) -> {
                    final var flat = new Frame(frame.indent(), Mode.FLAT, contents);
                    final var fits = frame.mode() == Mode.FLAT || (!preferBreak && fitsForward(flat, column, lineWidth, stack, work));
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
            output.append('\n')
                    .repeat(' ', Math.max(0, leadingSpaces(line) + shift))
                    .append(line, leadingSpaces(line), line.length());
        });
        return output.length() - output.lastIndexOf("\n") - 1;
    }

    private boolean isContent(final Doc doc) {
        return switch (doc) {
            case Doc.Text(var text) -> !text.isBlank();
            case Doc.Verbatim(var text, var _) -> !text.isEmpty();
            case Doc.BlockComment _ -> true;
            default -> false;
        };
    }

    private boolean isCode(final Doc doc) {
        return isContent(doc) && !(doc instanceof Doc.Text(var text) && (startsALineComment(text) || startsABlockComment(text)));
    }

    private boolean startsALineComment(final String text) {
        return text.stripLeading().startsWith("//");
    }

    private boolean startsABlockComment(final String text) {
        return text.stripLeading().startsWith("/*");
    }

    private String currentLine(final StringBuilder output) {
        return output.substring(output.lastIndexOf("\n") + 1);
    }

    private int newline(final StringBuilder output, final int verbatimEnd, final int indent) {
        stripTrailingWhitespace(output, verbatimEnd);
        output.append('\n').repeat(' ', indent);
        return indent;
    }

    private int leadingSpaces(final String line) {
        return (int) line.chars()
                .takeWhile(character -> character == ' ')
                .count();
    }

    private void stripTrailingWhitespace(final StringBuilder output, final int verbatimEnd) {
        final var trailing = (int) IntStream.iterate(output.length() - 1, index -> index >= 0 && isSpaceOrTab(output.charAt(index)), index -> index
                        - 1)
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
    private boolean fitsForward(final Frame candidate,
                                final int column,
                                final int lineWidth,
                                final ArrayDeque<Frame> upcoming,
                                final ArrayDeque<Frame> work) {
        return fits(candidate, column, lineWidth, upcoming, work, Reach.FIRST_LINE);
    }

    private Reach reachOf(final Doc.Choice.Fit fit) {
        return switch (fit) {
            case EVERY_LINE -> Reach.EVERY_LINE;
            case OPENING_LINE -> Reach.OPENING_LINE;
            case FIRST_BREAK -> Reach.FIRST_BREAK;
            case FIRST_BREAK_WITHOUT_SPLITTING_A_CHAIN -> Reach.FIRST_BREAK_WITHOUT_SPLITTING_A_CHAIN;
        };
    }

    private boolean stopsAtTheFirstBreak(final Reach reach) {
        return reach == Reach.FIRST_BREAK || reach == Reach.FIRST_BREAK_WITHOUT_SPLITTING_A_CHAIN;
    }

    private boolean fits(final Frame candidate,
                         final int startColumn,
                         final int lineWidth,
                         final ArrayDeque<Frame> upcoming,
                         final ArrayDeque<Frame> work,
                         final Reach reach) {
        var column = startColumn;
        var effectiveReach = reach;
        work.clear();
        work.push(candidate);
        final var iterator = upcoming.iterator();
        var consumedCandidate = false;
        var lineCommentEnded = false;
        var blockCommentEnded = false;
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
            if ((lineCommentEnded || blockCommentEnded) && consumedCandidate) {
                return true;
            }
            if ((lineCommentEnded && isContent(frame.doc())) || (blockCommentEnded && isCode(frame.doc()))) {
                return false;
            }
            switch (frame.doc()) {
                case Doc.Text(var comment) when startsALineComment(comment) -> {
                    if (column + comment.length() > lineWidth) {
                        return false;
                    }
                    if (consumedCandidate) {
                        return true;
                    }
                    lineCommentEnded = true;
                }
                case Doc.Text(var comment) when startsABlockComment(comment) -> {
                    if (column + comment.length() > lineWidth) {
                        return false;
                    }
                    if (consumedCandidate) {
                        return true;
                    }
                    column += comment.length();
                    blockCommentEnded = true;
                }
                case Doc.Text(var text) -> column += text.length();
                case Doc.Verbatim(var text, var _) -> {
                    final var firstNewline = text.indexOf('\n');
                    column += firstNewline >= 0 ? firstNewline : text.length();
                }
                case Doc.BlockComment(var lines, var _) -> {
                    column += lines.getFirst().length();
                    if (effectiveReach == Reach.FIRST_LINE && column <= lineWidth) {
                        return consumedCandidate;
                    }
                }
                case Doc.Line _ when frame.mode() == Mode.FLAT -> column++;
                case Doc.SoftLine _ when frame.mode() == Mode.FLAT -> {}
                case Doc.HardLine _, Doc.LineStart _ -> {
                    lineCommentEnded = false;
                    blockCommentEnded = false;
                    if (consumedCandidate || effectiveReach == Reach.OPENING_LINE || stopsAtTheFirstBreak(effectiveReach)) {
                        return true;
                    }
                    if (effectiveReach == Reach.FIRST_LINE) {
                        return false;
                    }
                    if (effectiveReach == Reach.NEXT_LINE_FIRST_BREAK) {
                        effectiveReach = Reach.FIRST_BREAK;
                    }
                    column = frame.indent();
                }
                case Doc.Line _, Doc.SoftLine _ -> {
                    if (consumedCandidate || effectiveReach == Reach.FIRST_LINE || stopsAtTheFirstBreak(effectiveReach)) {
                        return true;
                    }
                    if (effectiveReach == Reach.OPENING_LINE) {
                        return false;
                    }
                    column = frame.indent();
                }
                case Doc.Indent(var contents) -> work.push(new Frame(frame.indent() + INDENT_SIZE, frame.mode(), contents));
                case Doc.Align(var contents) -> work.push(new Frame(column, frame.mode(), contents));
                case Doc.Group(var contents, var preferBreak) -> {
                    if (stopsAtTheFirstBreak(effectiveReach) && !consumedCandidate) {
                        work.push(new Frame(frame.indent(), Mode.BREAK, contents));
                    } else if (preferBreak && !consumedCandidate && effectiveReach != Reach.EVERY_LINE) {
                        return false;
                    } else {
                        work.push(new Frame(frame.indent(), Mode.FLAT, contents));
                    }
                }
                case Doc.IfBreak(var breakContents, var flatContents) ->
                    work.push(new Frame(frame.indent(), frame.mode(), frame.mode() == Mode.BREAK ? breakContents : flatContents));
                case Doc.Choice(var preferred, var fallback, var _, var chainWidth) -> {
                    if (chainWidth >= 0 && !consumedCandidate && column + chainWidth > CHAIN_COLUMN) {
                        if (effectiveReach != Reach.FIRST_BREAK && effectiveReach != Reach.NEXT_LINE_FIRST_BREAK) {
                            return false;
                        }
                        work.push(new Frame(frame.indent(), Mode.BREAK, fallback));
                    } else {
                        work.push(new Frame(frame.indent(), frame.mode(), frame.mode() == Mode.BREAK && chainWidth < 0 ? fallback : preferred));
                    }
                }
                case Doc.Concat(var parts) -> {
                    for (var index = parts.size() - 1; index >= 0; index--) {
                        work.push(new Frame(frame.indent(), frame.mode(), parts.get(index)));
                    }
                }
            }
        }
        return false;
    }

    private enum Mode { FLAT, BREAK }

    private enum Reach { FIRST_LINE, OPENING_LINE, EVERY_LINE, FIRST_BREAK, FIRST_BREAK_WITHOUT_SPLITTING_A_CHAIN, NEXT_LINE_FIRST_BREAK }

    private record Frame(int indent, Mode mode, Doc doc) {}
}
