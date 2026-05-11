package io.javafmt.lint;

import io.javafmt.parser.ParsedUnit;

import java.util.List;

/**
 * Ensures the source ends with exactly one newline character. File-level rule — no AST walk
 * needed.
 */
public final class NewlineAtEndOfFile implements LintRule {

    @Override
    public String name() {
        return "NewlineAtEndOfFile";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
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
