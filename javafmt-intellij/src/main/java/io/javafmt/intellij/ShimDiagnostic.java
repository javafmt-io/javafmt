package io.javafmt.intellij;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One diagnostic line from {@code javafmt --diagnostics=json}.
 *
 * <p>Hand-rolled rather than using a JSON library: this module ships inside the IDE, where a
 * bundled library is a version-compatibility risk and a private copy is dead weight for a
 * five-member object of a shape javafmt itself controls. A line that does not match is dropped —
 * stdout is authoritative for the formatted text, so an unparseable diagnostic must never cost the
 * user their reformat.
 */
public record ShimDiagnostic(String kind, String message, int line, int col) {

    private static final Pattern KIND = Pattern.compile("\"kind\"\\s*:\\s*\"([^\"]*)\"");

    private static final Pattern LINE = Pattern.compile("\"line\"\\s*:\\s*(-?\\d+)");

    private static final Pattern COL = Pattern.compile("\"col\"\\s*:\\s*(-?\\d+)");

    /**
     * Decodes one NDJSON diagnostic line, or empty if it does not look like one. Never throws: this
     * reads a subprocess's stderr while stdout carries the authoritative formatted source, so a
     * malformed diagnostic must never cost the user their reformat — it is dropped instead.
     */
    public static Optional<ShimDiagnostic> parse(final String jsonLine) {
        if (jsonLine == null || jsonLine.isBlank() || !jsonLine.trim().startsWith("{")) {
            return Optional.empty();
        }
        final var messageSpan = extractMessage(jsonLine);
        if (messageSpan.isEmpty()) {
            return Optional.empty();
        }
        final var span = messageSpan.get();
        // The message value can contain text that looks like `"line":5` (a diagnostic quoting
        // source that itself resembles JSON). Searching kind/line/col outside the message span,
        // rather than over the whole raw line, keeps such text from shadowing the real members.
        final var outside = jsonLine.substring(0, span.start()) + jsonLine.substring(span.endExclusive());
        final var kind = group(KIND, outside);
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ShimDiagnostic(kind.get(), span.message(), number(LINE, outside), number(COL, outside)));
    }

    private static Optional<String> group(final Pattern p, final String s) {
        final var m = p.matcher(s);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static Optional<MessageSpan> extractMessage(final String jsonLine) {
        final var start = jsonLine.indexOf("\"message\"");
        if (start < 0) {
            return Optional.empty();
        }
        var i = start + 9;
        while (i < jsonLine.length() && jsonLine.charAt(i) != ':') {
            i++;
        }
        if (i >= jsonLine.length()) {
            return Optional.empty();
        }
        i++;
        while (i < jsonLine.length() && jsonLine.charAt(i) != '"') {
            i++;
        }
        if (i >= jsonLine.length()) {
            return Optional.empty();
        }
        i++;
        final var sb = new StringBuilder();
        while (i < jsonLine.length()) {
            final var c = jsonLine.charAt(i);
            if (c == '"') {
                return Optional.of(new MessageSpan(unescape(sb.toString()), start, i + 1));
            }
            if (c == '\\' && i + 1 < jsonLine.length()) {
                sb.append(c);
                i++;
                sb.append(jsonLine.charAt(i));
            } else {
                sb.append(c);
            }
            i++;
        }
        return Optional.empty();
    }

    private static int number(final Pattern p, final String s) {
        final var m = p.matcher(s);
        if (m.find()) {
            final var numStr = m.group(1);
            try {
                return Integer.parseInt(numStr);
            } catch (final NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static String unescape(final String raw) {
        final var sb = new StringBuilder(raw.length());
        for (var i = 0; i < raw.length(); i++) {
            final var c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                sb.append(c);
                continue;
            }
            final var next = raw.charAt(++i);
            switch (next) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (i + 4 < raw.length()) {
                        final var hexStr = raw.substring(i + 1, i + 5);
                        try {
                            sb.append((char) Integer.parseInt(hexStr, 16));
                            i += 4;
                        } catch (final NumberFormatException e) {
                            sb.append('u');
                        }
                    } else {
                        sb.append('u');
                    }
                }
                default -> sb.append(next);
            }
        }
        return sb.toString();
    }

    public boolean isInternal() {
        return "internal".equals(kind);
    }

    /**
     * Whether this diagnostic is advisory rather than a failure. javafmt's warning-only lint rules
     * ({@code HideUtilityClassConstructor}, {@code MissingSwitchDefault}, {@code FallThrough} and
     * the rest) report {@code "warning"}; the CLI still exits 0 and still prints the fully formatted
     * source, so a warning must not cost the user their reformat.
     *
     * <p>Every other {@code kind} counts as a failure, including one this build has never heard of.
     * The vocabulary is javafmt's own and can grow, and a kind added later is far more likely to be
     * another way of saying "this did not work" than a second advisory — so an unrecognised kind
     * leaves the file alone and tells the user, rather than installing output of unknown standing.
     */
    public boolean isAdvisory() {
        return "warning".equals(kind);
    }

    /** The decoded {@code message} value together with the raw span (key through closing quote) it occupied. */
    private record MessageSpan(String message, int start, int endExclusive) {}
}
