package io.javafmt.lint;

import io.javafmt.parser.ParsedUnit;

/**
 * A lint rule that walks a {@link ParsedUnit} and emits text edits to be applied to the
 * source, optionally accompanied by warning diagnostics for unsafe-to-fix cases. Rules
 * must be safe to apply unconditionally (javafmt has no opt-in fixes), and must converge:
 * re-running a rule on its own output must produce zero edits.
 *
 * <p>Following ruff's philosophy, a rule should never produce an edit that breaks
 * compilation or changes program behavior. When the rule's opinion would require such an
 * edit, it should emit a {@link io.javafmt.Diagnostic.Warning} instead
 * and leave the source alone.
 */
public interface LintRule {

    String name();

    LintResult apply(ParsedUnit unit);
}
