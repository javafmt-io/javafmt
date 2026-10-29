package io.javafmt.parser;

/**
 * Per-source result of {@link JavaParser#parseAndAttribute(java.util.List, String)}. Mirrors
 * {@link ParseOutcome}'s shape: parse failures surface per-source so the rest of the batch
 * still produces formatted output.
 */
public sealed interface AttributedOutcome {

    record Ok(AttributedUnit unit) implements AttributedOutcome {}

    /**
     * A source the batch could not hand back attributed. {@code error} is a {@link ParseException}
     * when the source itself is at fault, and any other {@link RuntimeException} when javac's
     * analyzer failed on source that parsed cleanly. Callers must keep the two apart: the second
     * is not a problem with the user's code, and reporting it as one would tell users their file
     * has a syntax error it does not have.
     */
    record Failed(RuntimeException error) implements AttributedOutcome {}
}
