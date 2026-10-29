package io.javafmt;

import java.util.List;
import java.util.Objects;

/**
 * Formatter configuration.
 *
 * @param excludes ANT-style glob patterns (matched by {@link java.nio.file.FileSystem#getPathMatcher
 *     "glob:..."}) of source paths that integration modules should skip. {@code javafmt-core}
 *     itself does no path filtering — this field is configuration that CLI, Maven, and Spotless
 *     consume. Empty by default. Patterns are matched against both the full path and the bare
 *     filename; a match in either drops the file from the run.
 * @param verifyOutput when {@code true}, the formatted output is checked against the post-lint
 *     source for token and comment preservation before being returned. On a mismatch the
 *     input is returned unchanged with a {@link Diagnostic.InternalError}, so a renderer
 *     coverage gap costs the user a reformat rather than their code. On by default; costs an
 *     extra lex of both strings per file. See {@link io.javafmt.parser.RoundTripVerifier}.
 */
public record JavafmtConfig(List<String> excludes, boolean verifyOutput, boolean lint, Analysis analysis) implements java.io.Serializable {

    public JavafmtConfig {
        Objects.requireNonNull(excludes, "excludes");
        Objects.requireNonNull(analysis, "analysis");
        excludes = List.copyOf(excludes);
    }

    public JavafmtConfig(final List<String> excludes, final boolean verifyOutput, final boolean lint) {
        this(excludes, verifyOutput, lint, new Analysis.ParseOnly());
    }

    public JavafmtConfig() {
        this(List.of(), true, true);
    }

    /** Returns a copy with {@link #excludes()} replaced by {@code newExcludes}. */
    public JavafmtConfig withExcludes(final List<String> newExcludes) {
        return new JavafmtConfig(newExcludes, verifyOutput, lint, analysis);
    }

    /** Returns a copy with {@link #verifyOutput()} set to {@code verify}. */
    public JavafmtConfig withVerifyOutput(final boolean verify) {
        return new JavafmtConfig(excludes, verify, lint, analysis);
    }

    public JavafmtConfig withAnalysis(final Analysis newAnalysis) {
        return new JavafmtConfig(excludes, verifyOutput, lint, newAnalysis);
    }

    /**
     * Turns the lint pass off, leaving layout formatting only.
     *
     * <p>Exists so javafmt can be compared like for like against formatters that only lay code out:
     * javafmt's normal timing includes inserting {@code final}, adding braces and removing unused
     * imports, plus the re-parse each rewrite costs. Not intended as a shipping mode — no
     * integration exposes it — and the lint rules are a large part of what javafmt is for.
     */
    public JavafmtConfig withLint(final boolean runLint) {
        return new JavafmtConfig(excludes, verifyOutput, runLint, analysis);
    }

    /** The default configuration: no excludes, output verification on, lint pass on, parse-only. */
    public static JavafmtConfig defaults() {
        return new JavafmtConfig();
    }

    public sealed interface Analysis extends java.io.Serializable {

        record ParseOnly() implements Analysis {}

        record Attributed(String classpath) implements Analysis {

            public Attributed {
                Objects.requireNonNull(classpath, "classpath");
            }
        }
    }
}
