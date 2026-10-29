package io.javafmt.lint;

/**
 * A lint rule that walks a {@link io.javafmt.parser.ParsedUnit} (obtained via the supplied
 * {@link LintContext}) and emits text edits to be applied to the source, optionally
 * accompanied by warning diagnostics for unsafe-to-fix cases. Rules must be safe to apply
 * unconditionally (javafmt has no opt-in fixes), and must converge: re-running a rule on
 * its own output must produce zero edits.
 *
 * <p>Following ruff's philosophy, a rule should never produce an edit that breaks
 * compilation or changes program behavior. When the rule's opinion would require such an
 * edit, it should emit a {@link io.javafmt.Diagnostic.Warning} instead and leave the
 * source alone.
 *
 * <p>Rules opt in to type-aware checks by inspecting {@link LintContext#attribution()};
 * a present {@link io.javafmt.parser.Attribution} means the caller passed {@code --analyze}
 * plus a classpath, and the rule may use {@code Trees}/{@code Types}/{@code Elements} for
 * resolved type information. When absent, the rule must either fall back to a textual
 * heuristic or skip the deeper check entirely.
 */
public interface LintRule {

    default String name() {
        return getClass().getSimpleName();
    }

    LintResult apply(LintContext ctx);
}
