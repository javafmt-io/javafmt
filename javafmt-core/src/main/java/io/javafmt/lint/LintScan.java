package io.javafmt.lint;

import com.sun.source.util.TreePath;
import io.javafmt.Diagnostic;
import io.javafmt.parser.ChainPathScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

/** The scan-from-root template shared by rules whose apply() is one visitor over the unit. */
@UtilityClass
final class LintScan {

    /** Runs {@code scanner(sink)} over the unit and wraps what the sink collected as warnings. */
    LintResult diagnostics(final LintContext ctx, final Function<List<Diagnostic>, ChainPathScanner<Void, Void>> scanner) {
        final var collected = new ArrayList<Diagnostic>();
        scanner.apply(collected).scan(new TreePath(ctx.unit().tree()), null);
        return new LintResult(List.of(), List.copyOf(collected));
    }

    LintResult edits(final LintContext ctx, final Function<List<LintEdit>, ChainPathScanner<Void, Void>> scanner) {
        final var collected = new ArrayList<LintEdit>();
        scanner.apply(collected).scan(new TreePath(ctx.unit().tree()), null);
        return LintResult.ofEdits(List.copyOf(collected));
    }
}
