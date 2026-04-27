package io.github.jschneidereit.grind.lint;

import io.github.jschneidereit.grind.parser.ParsedUnit;

import java.util.List;

/**
 * A lint rule that walks a {@link ParsedUnit} and emits text edits to be applied to the
 * source. Rules must be safe to apply unconditionally (grind has no opt-in fixes), and
 * must converge: re-running a rule on its own output must produce zero edits.
 */
public interface LintRule {

    String name();

    List<LintEdit> apply(ParsedUnit unit);
}
