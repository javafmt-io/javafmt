package io.javafmt.builder;

import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.CommentToken;
import io.javafmt.parser.ParsedUnit;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@UtilityClass
final class LooseComments {

    Doc weave(final Doc doc, final ParsedUnit unit) {
        if (unit.loose().isEmpty()) {
            return doc;
        }
        final var source = unit.source();
        final var significantBefore = significantBefore(source);
        final var insertions = unit.loose().stream()
                .sorted(Comparator.comparingInt(CommentToken::start))
                .map(
                        c -> new Insertion(
                                significantBefore[c.start()], c, source.substring(source.lastIndexOf('\n', c.start() - 1) + 1, c.start()).isBlank()))
                .toList();
        return new Weaver(insertions).transform(doc);
    }

    private boolean[] insideComments(final String source) {
        final var inComment = new boolean[source.length()];
        CommentScanner.scan(source).forEach(c -> IntStream.range(c.start(), c.end()).forEach(i -> inComment[i] = true));
        return inComment;
    }

    private int[] significantBefore(final String source) {
        final var inComment = insideComments(source);
        final var counts = new int[source.length() + 1];
        IntStream.range(0, source.length())
                .forEach(i -> counts[i + 1] = counts[i] + (inComment[i] || Character.isWhitespace(source.charAt(i)) ? 0 : 1));
        return counts;
    }

    private static final String OPENERS = "<([";

    private record Insertion(int significantBefore, CommentToken comment, boolean ownLine) {}

    private static final class Weaver {

        private final List<Insertion> insertions;

        private int next;

        private int count;

        private boolean spaceBeforeCode;

        Weaver(final List<Insertion> insertions) {
            this.insertions = insertions;
        }

        Doc transform(final Doc doc) {
            if (next >= insertions.size() && !spaceBeforeCode) {
                return doc;
            }
            return switch (doc) {
                case Doc.Text(var text) -> text(doc, text);
                case Doc.Verbatim(var text, var _) -> atom(doc, significantBefore(text)[text.length()]);
                case Doc.BlockComment _, Doc.Line _, Doc.SoftLine _, Doc.HardLine _, Doc.LineStart _ -> {
                    spaceBeforeCode = false;
                    yield doc;
                }
                case Doc.Indent(var inner) -> new Doc.Indent(transform(inner));
                case Doc.Align(var inner) -> new Doc.Align(transform(inner));
                case Doc.Group(var inner, var preferBreak) -> new Doc.Group(transform(inner), preferBreak);
                case Doc.Concat(var parts) -> new Doc.Concat(parts.stream().map(this::transform).toList());
                case Doc.Fill(var parts) -> new Doc.Fill(parts.stream().map(this::transform).toList());
                case Doc.IfBreak(var breakContents, var flatContents) -> {
                    final var both = branches(breakContents, flatContents);
                    yield new Doc.IfBreak(both.getFirst(), both.getLast());
                }
                case Doc.Choice(var preferred, var fallback, var fit) -> {
                    final var both = branches(preferred, fallback);
                    yield new Doc.Choice(both.getFirst(), both.getLast(), fit);
                }
            };
        }

        private List<Doc> branches(final Doc first, final Doc second) {
            final var startNext = next;
            final var startCount = count;
            final var startSpace = spaceBeforeCode;
            final var firstWoven = transform(first);
            final var endNext = next;
            final var endCount = count;
            final var endSpace = spaceBeforeCode;
            next = startNext;
            count = startCount;
            spaceBeforeCode = startSpace;
            final var secondWoven = transform(second);
            next = endNext;
            count = endCount;
            spaceBeforeCode = endSpace;
            return List.of(firstWoven, secondWoven);
        }

        private Doc atom(final Doc doc, final int significant) {
            final var parts = new ArrayList<Doc>(ownLineCommentsDue());
            parts.add(doc);
            count += significant;
            spaceBeforeCode = false;
            parts.addAll(sameLineCommentsDue(true, true));
            return parts.size() == 1 ? doc : new Doc.Concat(parts);
        }

        private Doc text(final Doc doc, final String original) {
            final var stripped = original.stripLeading();
            if (stripped.startsWith("//") || stripped.startsWith("/*")) {
                spaceBeforeCode = false;
                return doc;
            }
            final var text = spaceBeforeCode && !original.isEmpty() && startsCode(original.charAt(0)) ? " " + original : original;
            spaceBeforeCode = spaceBeforeCode && original.isEmpty();
            final var parts = new ArrayList<Doc>();
            final var inComment = insideComments(text);
            var from = 0;
            for (var i = 0; i < text.length(); i++) {
                if (Character.isWhitespace(text.charAt(i)) || inComment[i]) {
                    continue;
                }
                final var ownLine = ownLineCommentsDue();
                if (!ownLine.isEmpty()) {
                    parts.add(new Doc.Text(text.substring(from, i).stripTrailing()));
                    parts.addAll(ownLine);
                    from = i;
                }
                count++;
                final var atEnd = i + 1 == text.length();
                final var codeFollows = !atEnd && !Character.isWhitespace(text.charAt(i + 1));
                final var sameLine = sameLineCommentsDue(!codeFollows, OPENERS.indexOf(text.charAt(i)) < 0);
                if (!sameLine.isEmpty()) {
                    parts.add(new Doc.Text(text.substring(from, i + 1)));
                    parts.addAll(sameLine);
                    from = i + 1;
                    spaceBeforeCode = atEnd && !insertions.get(next - 1).comment().text().startsWith("//");
                }
            }
            if (parts.isEmpty()) {
                return text.equals(original) ? doc : new Doc.Text(text);
            }
            parts.add(new Doc.Text(text.substring(from)));
            return new Doc.Concat(parts);
        }

        private List<Doc> ownLineCommentsDue() {
            final var parts = new ArrayList<Doc>();
            while (next < insertions.size() && insertions.get(next).ownLine() && insertions.get(next).significantBefore() == count) {
                parts.add(Doc.LINE_START);
                parts.add(CommentDocs.renderComment(insertions.get(next).comment()));
                parts.add(Doc.HARD_LINE);
                next++;
            }
            return parts;
        }

        private boolean startsCode(final char character) {
            return Character.isJavaIdentifierPart(character) || character == '@' || character == '"' || character == '\'';
        }

        private List<Doc> sameLineCommentsDue(final boolean spaceFollows, final boolean spaceBefore) {
            final var parts = new ArrayList<Doc>();
            while (next < insertions.size() && !insertions.get(next).ownLine() && insertions.get(next).significantBefore() == count) {
                final var comment = insertions.get(next).comment();
                if (spaceBefore || !parts.isEmpty() || comment.text().startsWith("//")) {
                    parts.add(new Doc.Text(" "));
                }
                parts.add(CommentDocs.renderComment(comment));
                if (!spaceFollows && !comment.text().startsWith("//")) {
                    parts.add(new Doc.Text(" "));
                }
                next++;
            }
            return parts;
        }
    }
}
