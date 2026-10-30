package io.javafmt.corpus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The recorded state of the corpus gate: how many files javafmt still gets wrong today. Every
 * number here is a ceiling, not a target — a fix lowers it and the same commit lowers the recorded
 * value, so the file reads as a running account of what is left.
 *
 * <p>It exists because the gate was introduced against a formatter that already refuses hundreds of
 * ordinary files. Asserting zero from the start would have left {@code check} red for the whole of
 * the work the gate is supposed to measure.
 */
record CorpusBaseline(String os, Set<String> perOsKeys, int files, int unparseable, int refused, int notReparsing, int notIdempotent, int overWidth) {

    private static final String RESOURCE = "/corpus-baseline.properties";

    CorpusBaseline {
        Objects.requireNonNull(os, "os");
        perOsKeys = Set.copyOf(perOsKeys);
    }

    static CorpusBaseline load() {
        final var properties = new Properties();
        try (var in = CorpusBaseline.class.getResourceAsStream(RESOURCE)) {
            properties.load(Objects.requireNonNull(in, RESOURCE + " is missing from the corpusTest resources"));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
        final var os = os(System.getProperty("os.name", ""));
        final var perOsKeys = properties.stringPropertyNames()
                .stream()
                .filter(name -> name.endsWith("." + os))
                .map(name -> name.substring(0, name.length() - os.length() - 1))
                .collect(Collectors.toUnmodifiableSet());
        return new CorpusBaseline(
                os,
                perOsKeys,
                intProperty(properties, os, "files"),
                intProperty(properties, os, "unparseable"),
                intProperty(properties, os, "refused"),
                intProperty(properties, os, "notReparsing"),
                intProperty(properties, os, "notIdempotent"),
                intProperty(properties, os, "overWidth"));
    }

    String keyFor(final String key) {
        return perOsKeys.contains(key) ? key + "." + os : key;
    }

    static String os(final String osName) {
        final var name = osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("mac")) {
            return "mac";
        }
        if (name.startsWith("windows")) {
            return "windows";
        }
        return "linux";
    }

    private static int intProperty(final Properties properties, final String os, final String key) {
        final var value = properties.getProperty(key + "." + os, properties.getProperty(key));
        return Integer.parseInt(Objects.requireNonNull(value, key + " is missing from " + RESOURCE).trim());
    }
}
