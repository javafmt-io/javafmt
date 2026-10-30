package io.javafmt.cli;

import io.javafmt.Diagnostic;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/** Renders a {@link Diagnostic} as one line of NDJSON for {@code --diagnostics=json}. */
@UtilityClass
final class JsonDiagnostics {

    /**
     * The {@code kind} vocabulary is the sealed {@link Diagnostic} hierarchy plus {@code cli}, so a
     * client can tell a javafmt bug ({@code internal}) from a problem in the user's own source
     * without re-deriving it from a severity that has already collapsed the distinction.
     */
    String line(final String file, final Diagnostic d) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(d, "d");
        return object(d.kind(), d.message(), file, d.position().line(), d.position().column());
    }

    /** A message originating in the CLI rather than in a {@link Diagnostic}. */
    String cliLine(final String file, final String message) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(message, "message");
        return object("cli", message, file, 0, 0);
    }

    private String object(final String kind, final String message, final String file, final int line, final int col) {
        final var fileMember = file.isEmpty() ? "" : ",\"file\":\"" + escape(file) + "\"";
        return "{\"kind\":\"" + kind + "\",\"message\":\"" + escape(message) + "\"" + fileMember + ",\"line\":" + line + ",\"col\":" + col + "}";
    }

    private String escape(final String raw) {
        final var sb = new StringBuilder(raw.length() + 16);
        raw.codePoints().forEach(cp -> sb.append(escapeCodePoint(cp)));
        return sb.toString();
    }

    private String escapeCodePoint(final int cp) {
        return switch (cp) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            default -> cp < 0x20 ? String.format("\\u%04x", cp) : new String(Character.toChars(cp));
        };
    }
}
