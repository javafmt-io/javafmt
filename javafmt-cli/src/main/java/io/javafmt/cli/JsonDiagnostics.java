package io.javafmt.cli;

import io.javafmt.Diagnostic;
import lombok.experimental.UtilityClass;

import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;

/** Renders a {@link Diagnostic} as one line of NDJSON for {@code --diagnostics=json}. */
@UtilityClass
final class JsonDiagnostics {

    /**
     * The {@code kind} vocabulary is the sealed {@link Diagnostic} hierarchy plus {@code cli}, so a
     * client can tell a javafmt bug ({@code internal}) from a problem in the user's own source
     * without re-deriving it from a severity that has already collapsed the distinction.
     */
    String line(final String file, final Diagnostic diagnostic) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(diagnostic, "diagnostic");
        return object(
                diagnostic.kind(),
                diagnostic.message(),
                file,
                diagnostic.position().line(),
                diagnostic.position().column());
    }

    /** A message originating in the CLI rather than in a {@link Diagnostic}. */
    String cliLine(final String file, final String message) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(message, "message");
        return object("cli", message, file, 0, 0);
    }

    private String object(final String kind, final String message, final String file, final int line, final int column) {
        final var fileMember = file.isEmpty() ? "" : ",\"file\":\"" + escape(file) + "\"";
        return "{\"kind\":\"" + kind + "\",\"message\":\"" + escape(message) + "\"" + fileMember + ",\"line\":" + line + ",\"col\":" + column + "}";
    }

    private String escape(final String raw) {
        return raw.codePoints()
                .mapToObj(JsonDiagnostics::escapeCodePoint)
                .collect(Collectors.joining());
    }

    private String escapeCodePoint(final int codePoint) {
        return switch (codePoint) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            default -> codePoint < 0x20 ? "\\u" + HexFormat.of().toHexDigits((char) codePoint) : Character.toString(codePoint);
        };
    }
}
