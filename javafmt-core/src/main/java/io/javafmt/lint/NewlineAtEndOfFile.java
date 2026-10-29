package io.javafmt.lint;

import java.util.List;

/**
 * Ensures the source ends with exactly one newline character. File-level rule — no AST walk
 * needed.
 */
public final class NewlineAtEndOfFile implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        final var src = unit.source();
        if (src.isEmpty()) {
            return LintResult.EMPTY;
        }
        var contentEnd = src.length();
        while (contentEnd > 0 && src.charAt(contentEnd - 1) == '\n') {
            contentEnd--;
        }
        if (contentEnd == src.length()) {
            return LintResult.ofEdits(List.of(LintEdit.insert(src.length(), "\n")));
        }
        if (contentEnd == src.length() - 1) {
            return LintResult.EMPTY;
        }
        return LintResult.ofEdits(List.of(new LintEdit(contentEnd + 1, src.length(), "")));
    }
}
