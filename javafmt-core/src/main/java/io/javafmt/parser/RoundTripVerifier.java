package io.javafmt.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * Checks that formatting only moved whitespace around: the token stream and the comments of
 * the output must match those of the input.
 *
 * <p>The token comparison is against the <em>post-lint</em> source, not the user's original.
 * Lint rules rewrite code on purpose ({@code NeedBraces} adds braces, {@code LocalVarUseVar}
 * replaces a type with {@code var}), and each rewrite is already validated by the lint
 * engine's re-parse. What this verifies is the step after that — Doc build and print — which
 * is supposed to be purely a layout transformation. A mismatch there is a renderer coverage
 * gap silently deleting or corrupting code.
 *
 * <p>Three kinds of difference are tolerated because the formatter creates them by design:
 *
 * <ul>
 *   <li><b>Ordering.</b> The comparison is over multisets, not sequences. javafmt sorts
 *       imports and module directives, so a token's position is not an invariant. The cost
 *       is that a token which moves without being lost goes unnoticed; the benefit is that
 *       the check never fires on a reordering the formatter performs on purpose.</li>
 *   <li><b>Re-indentation of text blocks.</b> Moving a text block changes its incidental
 *       whitespace, so text-block tokens are compared by their stripped value
 *       ({@link String#stripIndent()}), which is exactly what the language spec says the
 *       literal means.</li>
 *   <li><b>Re-indentation of comments.</b> Comment text is compared line by line with
 *       surrounding whitespace stripped.</li>
 * </ul>
 */
@UtilityClass
public final class RoundTripVerifier {

    /**
     * Verifies that {@code after} preserves every token and comment of {@code before}.
     * Returns a description of what changed, or empty when the output is faithful.
     */
    public Optional<String> verify(final String before, final String after) {
        final var tokenDiff = compareTokens(normalizedTokens(before), normalizedTokens(after));
        return tokenDiff.isPresent() ? tokenDiff : verifyComments(before, after);
    }

    /**
     * Compares the token streams <em>in order</em>, except for the import declarations, which are
     * compared as a multiset because sorting them is the one reordering the renderer still performs
     * on its own. Everything else javafmt reorders — module directives, nested types, the
     * utility-class constructor, modifier order, annotations before keywords, C-style array
     * notation — is a lint edit, so it is already in the source this compares against.
     *
     * <p>This used to be a multiset everywhere, which cannot see a token that moves without being
     * lost. That is not a theoretical gap: {@code new T[n][]} printed as {@code new T[][n]} on
     * {@code java.base} and the output did not compile. The order comparison also says
     * <em>where</em>, which a multiset never could.
     */
    private Optional<String> compareTokens(final List<Token> before, final List<Token> after) {
        final var beforeParts = partition(before);
        final var afterParts = partition(after);
        final var importDiff = compare("import token", texts(beforeParts.imports()), texts(afterParts.imports()));
        if (importDiff.isPresent()) {
            return importDiff;
        }
        return firstDifference(beforeParts.body(), afterParts.body());
    }

    /** Where the two streams first differ, with a little of each side for context. */
    private Optional<String> firstDifference(final List<Token> before, final List<Token> after) {
        final var shared = Math.min(before.size(), after.size());
        final var at = IntStream.range(0, shared)
                .filter(i -> !before.get(i).text().equals(after.get(i).text()))
                .findFirst()
                .orElse(before.size() == after.size() ? -1 : shared);
        if (at < 0) {
            return Optional.empty();
        }
        return Optional.of(
                "tokens changed: the output diverges at token " + at + ", where the source has " + describe(texts(window(before, at)))
                        + " and the output has "
                        + describe(texts(window(after, at)))
                        + " ("
                        + before.size()
                        + " tokens in, "
                        + after.size()
                        + " out)");
    }

    /**
     * The offset in the compared source of the token where the two streams first differ, or empty
     * when they agree. Lets a caller point the user at a line rather than at a whole file.
     */
    public OptionalInt firstDifferingOffset(final String before, final String after) {
        final var beforeBody = partition(normalizedTokens(before)).body();
        final var afterBody = partition(normalizedTokens(after)).body();
        final var shared = Math.min(beforeBody.size(), afterBody.size());
        final var at = IntStream.range(0, shared)
                .filter(i -> !beforeBody.get(i).text().equals(afterBody.get(i).text()))
                .findFirst();
        if (at.isPresent()) {
            return OptionalInt.of(beforeBody.get(at.getAsInt()).start());
        }
        return beforeBody.size() > shared ? OptionalInt.of(beforeBody.get(shared).start()) : OptionalInt.empty();
    }

    private List<Token> window(final List<Token> tokens, final int at) {
        return tokens.subList(Math.min(at, tokens.size()), Math.min(at + 5, tokens.size()));
    }

    private List<String> texts(final List<Token> tokens) {
        return tokens.stream().map(Token::text).toList();
    }

    /**
     * Splits a token stream into the tokens of the {@code import} declarations and everything else.
     */
    private Partition partition(final List<Token> tokens) {
        final var imports = new ArrayList<Token>();
        final var body = new ArrayList<Token>();
        var inImport = false;
        for (var token : tokens) {
            inImport = inImport || token.text().equals("import");
            (inImport ? imports : body).add(token);
            inImport = inImport && !token.text().equals(";");
        }
        return new Partition(List.copyOf(imports), List.copyOf(body));
    }

    /**
     * The comment half of {@link #verify} on its own, so a caller can run it against a different
     * baseline than the tokens. {@code Javafmt} runs it against the user's original source rather
     * than the post-lint source: a lint rule that deletes a comment removes it from both sides of
     * the ordinary comparison, which is exactly how two rules deleted comments unnoticed.
     */
    public Optional<String> verifyComments(final String before, final String after) {
        return compare("comment", normalizedComments(before), normalizedComments(after));
    }

    private List<Token> normalizedTokens(final String source) {
        return TokenScanner.scan(source)
                .stream()
                .map(t -> new Token(normalizeToken(t.text()), t.start()))
                .toList();
    }

    private List<String> normalizedComments(final String source) {
        return CommentScanner.scan(source)
                .stream()
                .map(c -> normalizeComment(c.text()))
                .toList();
    }

    /**
     * Text blocks carry their indentation as content, and moving one legitimately rewrites
     * that indentation. Comparing the stripped value compares what the literal actually
     * denotes.
     */
    private String normalizeToken(final String text) {
        if (!text.startsWith("\"\"\"") || text.length() < 6 || !text.endsWith("\"\"\"")) {
            return text;
        }
        return "\"\"\"" + text.substring(3, text.length() - 3).stripIndent() + "\"\"\"";
    }

    private String normalizeComment(final String text) {
        return text.lines()
                .map(String::strip)
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }

    private Optional<String> compare(final String kind, final List<String> before, final List<String> after) {
        if (before.stream().sorted().toList().equals(after.stream().sorted().toList())) {
            return Optional.empty();
        }
        final var dropped = describe(minus(before, after));
        final var added = describe(minus(after, before));
        final var counts = "(" + before.size() + " " + kind + "s in, " + after.size() + " out)";
        return Optional.of(kind + "s changed: output dropped " + dropped + " and added " + added + " " + counts);
    }

    /** Multiset difference {@code from - other}, preserving the order of {@code from}. */
    private List<String> minus(final List<String> from, final List<String> other) {
        final var remaining = new ArrayList<>(other);
        return from.stream().filter(s -> !remaining.remove(s)).toList();
    }

    private String describe(final List<String> items) {
        if (items.isEmpty()) {
            return "nothing";
        }
        final var shown = items.stream()
                .limit(5)
                .map(RoundTripVerifier::quote)
                .collect(java.util.stream.Collectors.joining(", "));
        return items.size() > 5 ? shown + " and " + (items.size() - 5) + " more" : shown;
    }

    private String quote(final String text) {
        final var oneLine = text.replace("\n", "\\n");
        return "`" + (oneLine.length() > 60 ? oneLine.substring(0, 60) + "..." : oneLine) + "`";
    }

    private record Partition(List<Token> imports, List<Token> body) {}

    /** A token's compared text plus where it came from, so a difference can be located. */
    private record Token(String text, int start) {}
}
