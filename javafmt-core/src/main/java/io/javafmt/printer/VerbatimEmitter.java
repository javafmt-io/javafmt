package io.javafmt.printer;

import lombok.experimental.UtilityClass;

/**
 * Emits a {@link io.javafmt.doc.Doc.Verbatim} into a {@link StringBuilder}, applying
 * column-shift to every line after the first so the literal stays visually aligned when
 * the printer relocates it.
 *
 * <p>Shift semantics:
 * <ul>
 *   <li>First line: emitted unchanged. Its width pushes {@code col} forward.</li>
 *   <li>Subsequent lines: shifted by {@code outputCol - sourceColumn}. Positive shift prepends
 *       spaces; negative shift strips leading spaces.</li>
 *   <li>The negative shift is clamped to {@code col - minLeadingWs} where {@code minLeadingWs} is
 *       the smallest leading-space count across content lines and the closing line. This bound
 *       keeps every content line landing at or right of the output {@code """} column (so the
 *       block stays visually attached to its opener) while preserving the parsed string content
 *       — Java's text-block stripping removes the common indent across content + closing lines,
 *       and a uniform shift preserves that common indent.</li>
 *   <li>If {@code sourceColumn < 0} the literal is emitted byte-for-byte (no shift).</li>
 * </ul>
 */
@UtilityClass
final class VerbatimEmitter {

    /**
     * Emit {@code value} into {@code sb} at column {@code col}, applying the shift defined by
     * {@code sourceColumn}. Returns the column after the last emitted line.
     */
    int emit(final StringBuilder sb, final int col, final String value, final int sourceColumn) {
        final int firstNewline = value.indexOf('\n');
        if (firstNewline < 0) {
            sb.append(value);
            return col + value.length();
        }
        if (sourceColumn < 0) {
            sb.append(value);
            return tailColumn(value);
        }
        final int requestedShift = col - sourceColumn;
        final int actualShift = requestedShift < 0
                ? Math.max(requestedShift, col - minLeadingWhitespaceAfterFirstLine(value, firstNewline))
                : requestedShift;
        sb.append(value, 0, firstNewline);
        sb.append('\n');
        int lastLineStart = sb.length();
        int i = firstNewline + 1;
        while (i < value.length()) {
            final int nl = value.indexOf('\n', i);
            final int lineEnd = nl < 0 ? value.length() : nl;
            appendShiftedLine(sb, value, i, lineEnd, actualShift);
            if (nl < 0) {
                break;
            }
            sb.append('\n');
            lastLineStart = sb.length();
            i = nl + 1;
        }
        return sb.length() - lastLineStart;
    }

    /**
     * Column position after a multi-line verbatim emitted without shifting: width of the
     * last line. Caller ensures {@code value} contains at least one {@code '\n'}.
     */
    private int tailColumn(final String value) {
        final int lastNewline = value.lastIndexOf('\n');
        return value.length() - lastNewline - 1;
    }

    /**
     * Smallest leading-whitespace count (spaces only) across non-blank lines after the first.
     * This is the maximum we can safely strip without changing the text block's parsed content:
     * Java's text-block stripping removes the common indent across content + closing lines and
     * ignores blank (whitespace-only) lines when computing that common indent. Blank lines may
     * have arbitrarily little whitespace and are handled per-line by the strip step, which
     * clamps to each line's actual leading-space count.
     */
    private int minLeadingWhitespaceAfterFirstLine(final String value, final int firstNewline) {
        int min = Integer.MAX_VALUE;
        int i = firstNewline + 1;
        while (i <= value.length()) {
            final int nl = value.indexOf('\n', i);
            final int lineEnd = nl < 0 ? value.length() : nl;
            int ws = 0;
            while (i + ws < lineEnd && value.charAt(i + ws) == ' ') {
                ws++;
            }
            final boolean isBlank = i + ws == lineEnd;
            if (!isBlank) {
                min = Math.min(min, ws);
            }
            if (nl < 0) {
                break;
            }
            i = nl + 1;
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /**
     * Applies {@code shift} to one line: prepends spaces for a positive shift, or strips up to
     * {@code -shift} leading spaces for a negative one. The caller's clamp guarantees
     * {@code -shift <= minLeadingWs}, but a blank line may have fewer than {@code -shift} spaces,
     * so stripping stops at the first non-space character rather than assuming the full count
     * is there to remove.
     */
    private void appendShiftedLine(final StringBuilder sb, final String value, final int start, final int end, final int shift) {
        if (shift > 0) {
            sb.repeat(' ', shift);
            sb.append(value, start, end);
        } else if (shift < 0) {
            int skip = 0;
            final int maxSkip = -shift;
            while (skip < maxSkip && start + skip < end && value.charAt(start + skip) == ' ') {
                skip++;
            }
            sb.append(value, start + skip, end);
        } else {
            sb.append(value, start, end);
        }
    }
}
