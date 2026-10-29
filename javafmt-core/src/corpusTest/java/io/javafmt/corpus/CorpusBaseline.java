package io.javafmt.corpus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;

/**
 * The recorded state of the corpus gate: how many files javafmt still gets wrong today. Every
 * number here is a ceiling, not a target — a fix lowers it and the same commit lowers the recorded
 * value, so the file reads as a running account of what is left.
 *
 * <p>It exists because the gate was introduced against a formatter that already refuses hundreds of
 * ordinary files. Asserting zero from the start would have left {@code check} red for the whole of
 * the work the gate is supposed to measure.
 */
record CorpusBaseline(int files, int unparseable, int refused, int notReparsing, int notIdempotent, int overWidth) {

    private static final String RESOURCE = "/corpus-baseline.properties";

    static CorpusBaseline load() {
        final var properties = new Properties();
        try (var in = CorpusBaseline.class.getResourceAsStream(RESOURCE)) {
            properties.load(Objects.requireNonNull(in, RESOURCE + " is missing from the corpusTest resources"));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
        return new CorpusBaseline(
                intProperty(properties, "files"),
                intProperty(properties, "unparseable"),
                intProperty(properties, "refused"),
                intProperty(properties, "notReparsing"),
                intProperty(properties, "notIdempotent"),
                intProperty(properties, "overWidth"));
    }

    private static int intProperty(final Properties properties, final String key) {
        return Integer.parseInt(Objects.requireNonNull(properties.getProperty(key), key + " is missing from " + RESOURCE).trim());
    }
}
