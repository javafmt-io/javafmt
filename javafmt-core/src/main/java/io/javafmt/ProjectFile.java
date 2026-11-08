package io.javafmt;

import io.javafmt.lint.LintRules;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public sealed interface ProjectFile extends Serializable permits ProjectFile.None, ProjectFile.Settings, ProjectFile.Invalid {

    String NAME = ".javafmt";

    String ANALYZE_SKIP = "analyze.skip";

    Set<String> analyzeSkip();

    static ProjectFile none() {
        return new None();
    }

    static ProjectFile forFile(final Path sourceFile) {
        final var absolute = Objects.requireNonNull(sourceFile, "sourceFile").toAbsolutePath();
        return forDirectory(Objects.requireNonNullElse(absolute.getParent(), absolute));
    }

    static List<ProjectFile> forFiles(final Collection<Path> sourceFiles) {
        return sourceFiles.stream()
                .map(file -> file.toAbsolutePath().getParent())
                .filter(Objects::nonNull)
                .distinct()
                .map(ProjectFile::forDirectory)
                .distinct()
                .toList();
    }

    static ProjectFile forDirectory(final Path directory) {
        return Stream.iterate(
                        Objects.requireNonNull(directory, "directory")
                                .toAbsolutePath()
                                .normalize(),
                        Objects::nonNull,
                        Path::getParent)
                .filter(candidate -> Files.isRegularFile(candidate.resolve(NAME)) || Files.exists(candidate.resolve(".git")))
                .findFirst()
                .map(found -> found.resolve(NAME))
                .filter(Files::isRegularFile)
                .map(ProjectFile::read)
                .orElseGet(ProjectFile::none);
    }

    static ProjectFile read(final Path location) {
        try {
            return parse(location.toString(), Files.readString(location, StandardCharsets.UTF_8));
        } catch (final IOException e) {
            return new Invalid(location.toString(), location + ": cannot read: " + e.getMessage());
        }
    }

    static ProjectFile parse(final String location, final String text) {
        Objects.requireNonNull(location, "location");
        final var properties = new Properties();
        try {
            properties.load(new StringReader(Objects.requireNonNull(text, "text")));
        } catch (final IOException | IllegalArgumentException e) {
            return new Invalid(location, location + ": not a properties file: " + e.getMessage());
        }
        final var unknown = properties.stringPropertyNames().stream()
                .filter(key -> !key.equals(ANALYZE_SKIP))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            return new Invalid(location, location + ": unknown setting " + quoted(unknown) + "; the only setting is " + ANALYZE_SKIP);
        }
        final var skipped = Arrays.stream(
                        properties.getProperty(ANALYZE_SKIP, "").split(","))
                .map(String::strip)
                .filter(rule -> !rule.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        final var analysisRules = LintRules.analysisRuleNames();
        final var problems = skipped.stream()
                .filter(rule -> !analysisRules.contains(rule))
                .sorted()
                .map(ProjectFile::whyNotSkippable)
                .toList();
        if (!problems.isEmpty()) {
            return new Invalid(
                    location,
                    location + ": " + ANALYZE_SKIP + ": " + String.join("; ", problems) + ". The rules that use the classpath are "
                            + String.join(", ", analysisRules));
        }
        return new Settings(location, skipped);
    }

    private static String whyNotSkippable(final String rule) {
        return LintRules.ruleNames().contains(rule) ? rule + " does not use the classpath" : "\"" + rule + "\" is not a javafmt rule";
    }

    private static String quoted(final List<String> names) {
        return names.stream()
                .map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(", "));
    }

    record None() implements ProjectFile {

        @Override
        public Set<String> analyzeSkip() {
            return Set.of();
        }
    }

    record Settings(String location, Set<String> analyzeSkip) implements ProjectFile {

        public Settings {
            Objects.requireNonNull(location, "location");
            analyzeSkip = Collections.unmodifiableSortedSet(new TreeSet<>(analyzeSkip));
        }
    }

    record Invalid(String location, String problem) implements ProjectFile {

        public Invalid {
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(problem, "problem");
        }

        @Override
        public Set<String> analyzeSkip() {
            return Set.of();
        }
    }
}
