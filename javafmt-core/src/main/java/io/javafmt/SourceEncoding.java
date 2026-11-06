package io.javafmt;

import java.util.Objects;

/**
 * How a source file was written: whether it opened with a byte-order mark, and which line ending it
 * uses. javafmt works in LF with no BOM throughout — the parser strips the BOM, the comment renderer
 * strips {@code \r} — and puts both back on the way out.
 *
 * <p>Otherwise formatting a CRLF file rewrites every line of it. The diff a reviewer opens is then
 * the whole file, and whatever javafmt actually changed is invisible inside it. A file with mixed
 * endings is written back with whichever one it used most, since it has to be given one of them and
 * the majority is the smaller diff.
 */
public record SourceEncoding(boolean byteOrderMark, String lineEnding) {

    private static final char BOM = '﻿';

    public SourceEncoding {
        Objects.requireNonNull(lineEnding, "lineEnding");
    }

    /** What {@code source} was written with. */
    public static SourceEncoding of(final String source) {
        Objects.requireNonNull(source, "source");
        return new SourceEncoding(!source.isEmpty() && source.charAt(0) == BOM, dominantLineEnding(source));
    }

    /**
     * {@code source} as the rest of javafmt expects it: no BOM, LF only. A lone {@code \r} counts as
     * a line ending too — classic Mac OS files still turn up — and normalising it here keeps it from
     * reaching the parser as whitespace inside a line.
     */
    public String normalize(final String source) {
        Objects.requireNonNull(source, "source");
        final var withoutBom = byteOrderMark ? source.substring(1) : source;
        return withoutBom.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** {@code formatted} written back the way the input was written. */
    public String restore(final String formatted) {
        Objects.requireNonNull(formatted, "formatted");
        final var withEndings = lineEnding.equals("\n") ? formatted : formatted.replace("\n", lineEnding);
        return byteOrderMark ? BOM + withEndings : withEndings;
    }

    private static String dominantLineEnding(final String source) {
        var crlf = 0;
        var cr = 0;
        var lf = 0;
        for (var i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\r') {
                final var followedByLf = i + 1 < source.length() && source.charAt(i + 1) == '\n';
                if (followedByLf) {
                    crlf++;
                    i++;
                } else {
                    cr++;
                }
            } else if (source.charAt(i) == '\n') {
                lf++;
            }
        }
        if (crlf > lf && crlf >= cr) {
            return "\r\n";
        }
        return cr > lf && cr > crlf ? "\r" : "\n";
    }
}
