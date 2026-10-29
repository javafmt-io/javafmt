package io.javafmt.lint;

import io.javafmt.parser.ChainScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

/** The scan-from-root template shared by rules whose apply() is one visitor over the unit. */
@UtilityClass
final class LintScan {

    /** Runs {@code scanner(sink)} over the unit and returns what the sink collected. */
    <T> List<T> collect(final LintContext ctx, final Function<List<T>, ChainScanner<Void, Void>> scanner) {
        final var collected = new ArrayList<T>();
        scanner.apply(collected).scan(ctx.unit().tree(), null);
        return List.copyOf(collected);
    }

    LintResult edits(final LintContext ctx, final Function<List<LintEdit>, ChainScanner<Void, Void>> scanner) {
        return LintResult.ofEdits(collect(ctx, scanner));
    }
}
