package io.javafmt.doc;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

public sealed interface Doc
        permits Doc.Text, Doc.Verbatim, Doc.Line, Doc.SoftLine, Doc.HardLine, Doc.Indent, Doc.Group, Doc.Concat, Doc.IfBreak, Doc.Fill {

    /** The sole {@link Line}. The record holds no state, so every instance is interchangeable. */
    Line LINE = new Line();

    /** The sole {@link SoftLine}. The record holds no state, so every instance is interchangeable. */
    SoftLine SOFT_LINE = new SoftLine();

    /** The sole {@link HardLine}. The record holds no state, so every instance is interchangeable. */
    HardLine HARD_LINE = new HardLine();

    /**
     * Returns the flat-render width of {@code doc}, or {@code -1} if it contains a forced
     * break ({@link HardLine}, or a {@link Verbatim} whose body contains a newline). Useful
     * for renderers that want a per-construct break threshold independent of the line width
     * (e.g. break method chains at 80 chars even if the line itself fits 150).
     */
    static int flatWidthOrBreak(final Doc doc) {
        var total = 0;
        final var work = new ArrayDeque<Doc>();
        work.push(doc);
        while (!work.isEmpty()) {
            switch (work.pop()) {
                case Text(var s) -> total += s.length();
                case Verbatim(var s, var ignored) -> {
                    if (s.indexOf('\n') >= 0) {
                        return -1;
                    }
                    total += s.length();
                }
                case Line() -> total += 1;
                case SoftLine() -> {}
                case HardLine() -> {
                    return -1;
                }
                case Indent(var inner) -> work.push(inner);
                case Group(var inner, var ignored) -> work.push(inner);
                case IfBreak(var ignored, var flatContents) -> work.push(flatContents);
                case Concat(var ps) -> {
                    for (var i = ps.size() - 1; i >= 0; i--) {
                        work.push(ps.get(i));
                    }
                }
                case Fill(var ps) -> {
                    for (var i = ps.size() - 1; i >= 0; i--) {
                        work.push(ps.get(i));
                    }
                }
            }
        }
        return total;
    }

    /**
     * Whether the last thing {@code doc} prints is a {@code //} comment. Such a comment runs to the
     * end of its line, so anything appended after it is swallowed — a statement's own {@code ;}
     * included, which is how {@code + "zul" // Zulu} followed by {@code ;} on the next line printed
     * as {@code // Zulu;} and stopped compiling. A renderer that appends text after a scanned
     * subtree has to break the line first when this is true.
     *
     * <p>No token starts with {@code //}, so matching the rendered text is enough to tell a comment
     * from ordinary output.
     */
    static boolean endsWithLineComment(final Doc doc) {
        return switch (doc) {
            case Text(var s) -> s.stripTrailing().startsWith("//") || s.stripTrailing().contains("\n//");
            case Verbatim(var s, var ignored) -> s.stripTrailing().startsWith("//");
            case Indent(var inner) -> endsWithLineComment(inner);
            case Group(var inner, var ignored) -> endsWithLineComment(inner);
            case Concat(var parts) -> !parts.isEmpty() && endsWithLineComment(parts.getLast());
            case Fill(var parts) -> !parts.isEmpty() && endsWithLineComment(parts.getLast());
            case IfBreak(var breakContents, var ignored) -> endsWithLineComment(breakContents);
            case Line(), SoftLine(), HardLine() -> false;
        };
    }

    /**
     * Whether {@code doc} already contains somewhere a point at which the printer can, or must,
     * start a new line. Renderers use this to avoid introducing a break of their own when the
     * operand is able to absorb one: breaking after the {@code =} of an assignment whose value is
     * a wrappable call or operator chain only buys an extra indent level, since the value's own
     * group would have taken the break anyway.
     */
    static boolean canBreakInternally(final Doc doc) {
        final var work = new ArrayDeque<Doc>();
        work.push(doc);
        while (!work.isEmpty()) {
            switch (work.pop()) {
                case Line(), SoftLine(), HardLine() -> {
                    return true;
                }
                case Fill ignored -> {
                    return true;
                }
                case Verbatim(var s, var ignored) -> {
                    if (s.indexOf('\n') >= 0) {
                        return true;
                    }
                }
                case Text ignored -> {}
                case Indent(var inner) -> work.push(inner);
                case Group(var inner, var ignored) -> work.push(inner);
                case IfBreak(var breakContents, var ignored) -> work.push(breakContents);
                case Concat(var ps) -> ps.forEach(work::push);
            }
        }
        return false;
    }

    /**
     * Wraps {@code contents} in a continuation indent — two block indents (2 × INDENT_SIZE).
     * Used for wrapped continuations of a single logical line (parameter/argument lists,
     * record components, {@code extends}/{@code implements}/{@code permits} and {@code throws}
     * clauses, and method chains) so wrapped content never shares a column with the block body
     * beneath it. Mirrors Google Java Format's "continuation = 2× block" rule.
     */
    static Doc continuationIndent(final Doc contents) {
        Objects.requireNonNull(contents, "contents");
        return new Indent(new Indent(contents));
    }

    /** A member pushed one indent level in, opening on a fresh line: the body-brace idiom. */
    static Doc indentedLine(final Doc contents) {
        Objects.requireNonNull(contents, "contents");
        return new Indent(new Concat(List.of(HARD_LINE, contents)));
    }

    /** The closing "\n}" of a braced body. */
    static Stream<Doc> closeBrace() {
        return Stream.of(HARD_LINE, new Text("}"));
    }

    /** Concat of several part-streams without nesting Stream.concat towers. */
    @SafeVarargs
    static Doc concatOf(final Stream<Doc>... parts) {
        return new Concat(Arrays.stream(parts).flatMap(Function.identity()));
    }

    static Stream<Doc> intersperse(final Doc separator, final Stream<Doc> parts) {
        Objects.requireNonNull(separator, "separator");
        Objects.requireNonNull(parts, "parts");
        return parts.flatMap(d -> Stream.of(separator, d)).skip(1);
    }

    static Stream<Doc> intersperse(final List<Doc> separator, final Stream<Doc> parts) {
        Objects.requireNonNull(separator, "separator");
        Objects.requireNonNull(parts, "parts");
        final var sepArray = separator.toArray(Doc[]::new);
        return parts.flatMap(d -> Stream.concat(Arrays.stream(sepArray), Stream.of(d))).skip(sepArray.length);
    }

    record Text(String value) implements Doc {

        public Text {
            Objects.requireNonNull(value, "value");
            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Text value must not contain newline characters");
            }
        }
    }

    /**
     * A run of raw text emitted verbatim, including any embedded newlines. Used for constructs
     * whose internal whitespace is semantically significant — e.g. multi-line string literals
     * and text blocks — where the printer must not interfere with the source's internal layout.
     *
     * <p>{@code sourceColumn} is the 0-indexed column the literal occupied in the source. The
     * printer applies a uniform {@code shift = outputColumn - sourceColumn} to every line after
     * the first, so the literal's internal indentation tracks the relocated first line. The
     * shift is clamped: a negative shift never strips more leading whitespace than the
     * minimum content/closing line has — otherwise the parsed string content (after text-block
     * stripping) would change. A negative {@code sourceColumn} disables shifting (the literal
     * emits as-is).
     */
    record Verbatim(String value, int sourceColumn) implements Doc {

        public Verbatim {
            Objects.requireNonNull(value, "value");
        }
    }

    record Line() implements Doc {}

    record SoftLine() implements Doc {}

    record HardLine() implements Doc {}

    record Indent(Doc contents) implements Doc {

        public Indent {
            Objects.requireNonNull(contents, "contents");
        }
    }

    /**
     * A group whose contents render flat if they fit on the current line, otherwise break. When
     * {@code preferBreakOnAuthor} is true the group additionally prefers BREAK whenever the
     * enclosing mode is not already FLAT — the renderer's callers use this to honor an author's
     * deliberate multi-line layout for constructs like method chains and {@code &&}/{@code ||}
     * compounds even when they'd technically fit on one line.
     */
    record Group(Doc contents, boolean preferBreakOnAuthor) implements Doc {

        public Group {
            Objects.requireNonNull(contents, "contents");
        }

        public Group(final Doc contents) {
            this(contents, false);
        }
    }

    record Concat(List<Doc> parts) implements Doc {

        public Concat {
            Objects.requireNonNull(parts, "parts");
            parts = List.copyOf(parts);
        }

        public Concat(final Stream<Doc> docs) {
            this(Objects.requireNonNull(docs, "docs").toList());
        }
    }

    record IfBreak(Doc breakContents, Doc flatContents) implements Doc {

        public IfBreak {
            Objects.requireNonNull(breakContents, "breakContents");
            Objects.requireNonNull(flatContents, "flatContents");
        }
    }

    record Fill(List<Doc> parts) implements Doc {

        public Fill {
            Objects.requireNonNull(parts, "parts");
            parts = List.copyOf(parts);
            if ((parts.size() & 1) == 0) {
                throw new IllegalArgumentException("Fill parts must alternate content/separator/.../content (odd length)");
            }
            for (var i = 1; i < parts.size(); i += 2) {
                requireSeparator(parts.get(i));
            }
        }

        private static void requireSeparator(final Doc part) {
            final var isSeparator = switch (part) {
                case Line ignored -> true;
                case SoftLine ignored -> true;
                default -> false;
            };
            if (!isSeparator) {
                throw new IllegalArgumentException("Fill separators must be Line or SoftLine, got: " + part.getClass().getSimpleName());
            }
        }
    }
}
