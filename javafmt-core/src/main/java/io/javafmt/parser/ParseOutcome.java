package io.javafmt.parser;

/**
 * Per-source result of {@link JavaParser#parseUnits(java.util.List)}. The batch API
 * surfaces failures at individual indices instead of short-circuiting, so callers can
 * format the survivors.
 */
public sealed interface ParseOutcome {

    record Ok(ParsedUnit unit) implements ParseOutcome {}

    record Failed(ParseException error) implements ParseOutcome {}
}
