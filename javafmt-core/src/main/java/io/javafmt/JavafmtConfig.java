package io.javafmt;

import java.util.Objects;

/**
 * Formatter configuration.
 *
 * @param verifyOutput when {@code true}, the formatted output is checked against the post-lint
 *     source for token and comment preservation before being returned. On a mismatch the
 *     input is returned unchanged with a {@link Diagnostic.InternalError}, so a renderer
 *     coverage gap costs the user a reformat rather than their code. On by default; costs an
 *     extra lex of both strings per file. See {@link io.javafmt.parser.RoundTripVerifier}.
 */
public record JavafmtConfig(boolean verifyOutput, boolean lint, Analysis analysis) implements java.io.Serializable {

    public JavafmtConfig {
        Objects.requireNonNull(analysis, "analysis");
    }

    public JavafmtConfig() {
        this(true, true, new Analysis.ParseOnly());
    }

    /** Returns a copy with {@link #verifyOutput()} set to {@code verify}. */
    public JavafmtConfig withVerifyOutput(final boolean verify) {
        return new JavafmtConfig(verify, lint, analysis);
    }

    public JavafmtConfig withAnalysis(final Analysis newAnalysis) {
        return new JavafmtConfig(verifyOutput, lint, newAnalysis);
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
        return new JavafmtConfig(verifyOutput, runLint, analysis);
    }

    /** The default configuration: output verification on, lint pass on, parse-only. */
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
