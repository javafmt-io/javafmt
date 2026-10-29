package io.javafmt.printer;

import io.javafmt.doc.Doc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * The stack-machine printer shared by both strategies. The only decisions a strategy owns are
 * the two fit predicates: whether a Group may render flat, and whether Fill may pack the next
 * content element onto the line in progress.
 */
@UtilityClass
final class PrintEngine {

    final int INDENT_SIZE = 4;

    String print(final int lineWidth, final Doc doc, final FitPolicy policy) {
        Objects.requireNonNull(doc, "doc");
        final var sb = new StringBuilder();
        final var verbatimRanges = new ArrayList<int[]>();
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
                case Doc.Verbatim(var s, var sourceCol) -> {
                    final var vStart = sb.length();
                    col = VerbatimEmitter.emit(sb, col, s, sourceCol);
                    verbatimRanges.add(new int[] {vStart, sb.length(),});
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
                case Doc.Indent(var d) -> stack.push(new Frame(frame.indent() + INDENT_SIZE, frame.mode(), d));
                case Doc.Concat(var parts) -> {
                    for (var i = parts.size() - 1; i >= 0; i--) {
                        stack.push(new Frame(frame.indent(), frame.mode(), parts.get(i)));
                    }
                }
                case Doc.Group(var d, var preferBreak) -> {
                    final Mode mode;
                    if (frame.mode() == Mode.FLAT) {
                        mode = Mode.FLAT;
                    } else if (preferBreak) {
                        mode = Mode.BREAK;
                    } else {
                        final var candidate = new Frame(frame.indent(), Mode.FLAT, d);
                        mode = policy.groupFits(candidate, lineWidth - col, stack) ? Mode.FLAT : Mode.BREAK;
                    }
                    stack.push(new Frame(frame.indent(), mode, d));
                }
                case Doc.IfBreak(var breakContents, var flatContents) ->
                    stack.push(new Frame(frame.indent(), frame.mode(), frame.mode() == Mode.BREAK ? breakContents : flatContents));
                case Doc.Fill(var parts) -> {
                    final var newFrames = expandFill(parts, frame.indent(), col, lineWidth, policy);
                    for (var i = newFrames.size() - 1; i >= 0; i--) {
                        stack.push(newFrames.get(i));
                    }
                }
            }
        }
        return VerbatimEmitter.stripTrailingWhitespace(sb.toString(), verbatimRanges);
    }

    /**
     * Wadler-Lindig has no native Fill combinator, so the greedy per-separator pack from
     * {@link GreedyPrintStrategy} is preserved here verbatim. Reformulating Fill as nested
     * Groups is the principled WL alternative but would change every Fill call site's output
     * and is out of scope for this strategy.
     *
     * <p>The odd-length assertion is what makes the {@code parts.get(i + 1)} lookahead below
     * safe: every separator has a content element after it. {@link Doc.Fill}'s constructor is
     * the only producer and it rejects even lengths.
     */
    private List<Frame> expandFill(final List<Doc> parts, final int indent, final int col, final int lineWidth, final FitPolicy policy) {
        assert (parts.size() & 1) == 1 : "Fill parts must have odd length, got " + parts.size();
        final var out = new ArrayList<Frame>();
        var currentCol = col;
        var forceBreakNextSep = false;
        for (var i = 0; i < parts.size(); i++) {
            final var part = parts.get(i);
            final var isSep = (i & 1) == 1;
            if (!isSep) {
                out.add(new Frame(indent, Mode.FLAT, part));
                if (endsMidLine(part)) {
                    forceBreakNextSep = true;
                } else {
                    currentCol += Doc.flatWidthOrBreak(part);
                }
            } else {
                final var nextContent = parts.get(i + 1);
                final var sepFlatWidth = part instanceof Doc.Line ? 1 : 0;
                if (!forceBreakNextSep && policy.fillContentFits(nextContent, lineWidth - currentCol - sepFlatWidth)) {
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

    /**
     * Whether {@code part}'s own rendering ends mid-line — a {@link Doc.HardLine} or a
     * multi-line {@link Doc.Verbatim} inside it — which forces the next separator to BREAK
     * regardless of where the rendered cursor lands. See
     * {@code PrinterTest#fill_hardLineInsideContent_forcesNextSeparatorToBreak}.
     */
    private boolean endsMidLine(final Doc part) {
        return Doc.flatWidthOrBreak(part) < 0;
    }

    enum Mode { FLAT, BREAK }

    record Frame(int indent, Mode mode, Doc doc) {}

    interface FitPolicy {

        boolean groupFits(Frame candidate, int budget, ArrayDeque<Frame> upcoming);

        boolean fillContentFits(Doc content, int remaining);
    }
}
